package com.gladunalexander.lsmkv.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gladunalexander.lsmkv.sstable.Manifest;
import com.gladunalexander.lsmkv.sstable.SSTable;
import com.gladunalexander.lsmkv.sstable.SSTableMeta;
import com.gladunalexander.lsmkv.sstable.Slot;
import com.gladunalexander.lsmkv.wal.Wal;

import jakarta.annotation.PreDestroy;

/**
 * The LSM-tree engine. Week 6 replaces the single-file compaction with leveled compaction.
 *
 * <p>SSTables are organised into levels. Level 0 holds flushed memtables and may overlap;
 * from level 1 down, SSTables within a level are non-overlapping. When level 0 accumulates
 * too many files (or a deeper level exceeds its size budget), compaction merges that level
 * into the next one, keeping the newest record per key and rebuilding the target level as a
 * set of non-overlapping SSTables. Tombstones are dropped only once they reach the
 * bottom-most level, where no older value can survive them.
 *
 * <p>Reads consult the memtable, then level 0 newest-to-oldest, then for each deeper level
 * the single SSTable whose key range covers the key. The first source holding the key wins;
 * a tombstone means absent. Compaction is single-threaded and stop-the-world.
 */
@Component
@Primary
public class LsmStore implements Store {

    private final Path dataDir;
    private final ObjectMapper mapper;
    private final int memtableMaxEntries;
    private final int l0Trigger;
    private final int levelFanout;

    private final Manifest manifest;
    private final Wal wal;
    private Memtable memtable = new Memtable();

    public LsmStore(@Value("${lsmkv.data-dir:lsmkv-data}") String dataDir,
                    @Value("${lsmkv.memtable-max-entries:1024}") int memtableMaxEntries,
                    @Value("${lsmkv.l0-compaction-trigger:4}") int l0Trigger,
                    @Value("${lsmkv.level-fanout:10}") int levelFanout,
                    ObjectMapper mapper) {
        this.dataDir = Path.of(dataDir);
        this.mapper = mapper;
        this.memtableMaxEntries = memtableMaxEntries;
        this.l0Trigger = l0Trigger;
        this.levelFanout = levelFanout;
        try {
            Files.createDirectories(this.dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create data dir " + dataDir, e);
        }
        this.manifest = new Manifest(this.dataDir, mapper);
        deleteDanglingSstables();
        this.wal = new Wal(this.dataDir);
        recoverFromWal();
    }

    @Override
    public synchronized void put(String key, String value) {
        wal.appendPut(key, value); // durable before we acknowledge
        memtable.put(key, value);
        maybeFlush();
    }

    @Override
    public synchronized void delete(String key) {
        wal.appendDelete(key);
        memtable.delete(key); // tombstone
        maybeFlush();
    }

    @Override
    public synchronized Optional<String> get(String key) {
        Optional<Slot> hit = memtable.lookup(key);

        // Level 0: overlapping SSTables, newest (last) to oldest.
        List<SSTableMeta> l0 = manifest.level(0);
        for (int i = l0.size() - 1; i >= 0 && hit.isEmpty(); i--) {
            hit = sstable(l0.get(i)).lookup(key);
        }

        // Deeper levels: at most one non-overlapping SSTable per level covers the key.
        for (int n = 1; n <= manifest.maxLevel() && hit.isEmpty(); n++) {
            for (SSTableMeta meta : manifest.level(n)) {
                if (meta.covers(key)) {
                    hit = sstable(meta).lookup(key);
                    break;
                }
            }
        }

        return hit.filter(slot -> !slot.tombstone()).map(Slot::value);
    }

    @Override
    public synchronized SortedMap<String, String> scan(String start, String end) {
        // Merge oldest-to-newest so the newest record per key wins: deepest levels first,
        // then level 0 oldest-to-newest, then the memtable.
        TreeMap<String, String> merged = new TreeMap<>();
        for (int n = manifest.maxLevel(); n >= 1; n--) {
            for (SSTableMeta meta : manifest.level(n)) {
                merged.putAll(sstable(meta).entriesInRange(start, end));
            }
        }
        for (SSTableMeta meta : manifest.level(0)) {
            merged.putAll(sstable(meta).entriesInRange(start, end));
        }
        merged.putAll(memtable.rangeEntries(start, end));
        merged.values().removeIf(value -> value == null); // exclude deleted keys
        return merged;
    }

    private void maybeFlush() {
        if (memtable.size() >= memtableMaxEntries) {
            flush();
        }
    }

    /** Flushes the memtable to a new level-0 SSTable. Stop-the-world. */
    private void flush() {
        if (memtable.isEmpty()) {
            return;
        }
        Map<String, String> entries = memtable.entries(); // sorted by key
        String minKey = null;
        String maxKey = null;
        for (String key : entries.keySet()) {
            if (minKey == null) {
                minKey = key;
            }
            maxKey = key;
        }
        int id = manifest.nextId();
        String name = "sst-" + id + ".json";
        SSTable.write(dataDir.resolve(name), entries);
        manifest.add(new SSTableMeta(name, 0, minKey, maxKey));
        memtable = new Memtable();
        wal.reset(); // the flushed writes are now durable in the SSTable
        maybeCompact();
    }

    /** Runs leveled compaction until every level is within its budget. Stop-the-world. */
    private void maybeCompact() {
        while (true) {
            if (manifest.level(0).size() >= l0Trigger) {
                compact(0);
                continue;
            }
            int target = -1;
            for (int n = 1; n <= manifest.maxLevel(); n++) {
                if (manifest.level(n).size() > budget(n)) {
                    target = n;
                    break;
                }
            }
            if (target < 0) {
                return;
            }
            compact(target);
        }
    }

    /** Merges level {@code n} into level {@code n + 1}, rebuilding the latter non-overlapping. */
    private void compact(int n) {
        List<SSTableMeta> lower = manifest.level(n);      // newer
        List<SSTableMeta> upper = manifest.level(n + 1);  // older

        // Merge oldest-to-newest: upper level first, then the lower level (oldest-to-newest).
        TreeMap<String, String> merged = new TreeMap<>();
        for (SSTableMeta meta : upper) {
            merged.putAll(sstable(meta).entries());
        }
        for (SSTableMeta meta : lower) {
            merged.putAll(sstable(meta).entries());
        }

        // Tombstones can be discarded only when level n+1 is the bottom-most level.
        if (manifest.maxLevel() <= n + 1) {
            merged.values().removeIf(value -> value == null);
        }

        List<SSTableMeta> rebuilt = partitionIntoSstables(merged, n + 1);

        List<SSTableMeta> kept = new ArrayList<>();
        for (SSTableMeta meta : manifest.all()) {
            if (meta.level() != n && meta.level() != n + 1) {
                kept.add(meta);
            }
        }
        kept.addAll(rebuilt);
        manifest.replaceAll(kept);

        deleteFiles(lower);
        deleteFiles(upper);
    }

    /** Splits sorted entries into non-overlapping SSTables of at most {@code memtableMaxEntries}. */
    private List<SSTableMeta> partitionIntoSstables(SortedMap<String, String> entries, int level) {
        List<SSTableMeta> result = new ArrayList<>();
        int nextId = manifest.nextId();
        TreeMap<String, String> chunk = new TreeMap<>();
        for (Map.Entry<String, String> e : entries.entrySet()) {
            chunk.put(e.getKey(), e.getValue());
            if (chunk.size() >= memtableMaxEntries) {
                result.add(writeChunk(chunk, level, nextId++));
                chunk = new TreeMap<>();
            }
        }
        if (!chunk.isEmpty()) {
            result.add(writeChunk(chunk, level, nextId));
        }
        return result;
    }

    private SSTableMeta writeChunk(SortedMap<String, String> chunk, int level, int id) {
        String name = "sst-" + id + ".json";
        SSTable.write(dataDir.resolve(name), chunk);
        return new SSTableMeta(name, level, chunk.firstKey(), chunk.lastKey());
    }

    /** SSTable count a level may hold before it is compacted downward (grows by the fanout). */
    private int budget(int level) {
        return (int) (l0Trigger * Math.pow(levelFanout, level - 1));
    }

    private SSTable sstable(SSTableMeta meta) {
        return new SSTable(dataDir.resolve(meta.filename()));
    }

    private void deleteFiles(List<SSTableMeta> metas) {
        for (SSTableMeta meta : metas) {
            try {
                Files.deleteIfExists(dataDir.resolve(meta.filename()));
            } catch (IOException e) {
                throw new UncheckedIOException("failed to delete SSTable " + meta.filename(), e);
            }
        }
    }

    private void recoverFromWal() {
        for (Map.Entry<String, String> e : wal.replay().entrySet()) {
            if (e.getValue() == null) {
                memtable.delete(e.getKey());
            } else {
                memtable.put(e.getKey(), e.getValue());
            }
        }
    }

    /** Removes SSTable files (and stray temp files) not referenced by the MANIFEST. */
    private void deleteDanglingSstables() {
        Set<String> referenced = new HashSet<>();
        for (SSTableMeta meta : manifest.all()) {
            referenced.add(meta.filename());
        }
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(dataDir)) {
            for (Path p : dir) {
                String name = p.getFileName().toString();
                boolean stray = name.endsWith(".tmp")
                        || (name.startsWith("sst-") && name.endsWith(".json")
                            && !referenced.contains(name));
                if (stray) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to clean dangling SSTables", e);
        }
    }

    @PreDestroy
    public synchronized void close() {
        wal.close();
    }
}
