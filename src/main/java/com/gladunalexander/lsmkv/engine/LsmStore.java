package com.gladunalexander.lsmkv.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.gladunalexander.lsmkv.sstable.Slot;
import com.gladunalexander.lsmkv.wal.Wal;

import jakarta.annotation.PreDestroy;

/**
 * The LSM-tree engine. Week 4 adds deletes, tombstones, and compaction.
 *
 * <p>Deletes are written as tombstones: a marker that flows through the WAL, the memtable,
 * and flushes just like a put. A read stops at the first source (memtable, then SSTables
 * newest-to-oldest) that contains the key; if that record is a tombstone the key is treated
 * as absent.
 *
 * <p>Compaction merges all SSTables into a single one, keeping the newest record per key
 * and dropping tombstones, then atomically swaps the MANIFEST and deletes the old files. It
 * is single-threaded and stop-the-world, triggered once the SSTable count crosses a
 * threshold.
 */
@Component
@Primary
public class LsmStore implements Store {

    private final Path dataDir;
    private final ObjectMapper mapper;
    private final int memtableMaxEntries;
    private final int compactionThreshold;

    private final Manifest manifest;
    private final Wal wal;
    private Memtable memtable = new Memtable();

    public LsmStore(@Value("${lsmkv.data-dir:lsmkv-data}") String dataDir,
                    @Value("${lsmkv.memtable-max-entries:1024}") int memtableMaxEntries,
                    @Value("${lsmkv.compaction-threshold:4}") int compactionThreshold,
                    ObjectMapper mapper) {
        this.dataDir = Path.of(dataDir);
        this.mapper = mapper;
        this.memtableMaxEntries = memtableMaxEntries;
        this.compactionThreshold = compactionThreshold;
        try {
            Files.createDirectories(this.dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create data dir " + dataDir, e);
        }
        this.manifest = new Manifest(this.dataDir);
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
        if (hit.isEmpty()) {
            // Newest SSTable wins, so scan the manifest from newest (last) to oldest (first).
            List<String> sstables = manifest.sstables();
            for (int i = sstables.size() - 1; i >= 0 && hit.isEmpty(); i--) {
                hit = new SSTable(dataDir.resolve(sstables.get(i)), mapper).lookup(key);
            }
        }
        // A tombstone (or no hit at all) means the key is absent.
        return hit.filter(slot -> !slot.tombstone()).map(Slot::value);
    }

    @Override
    public synchronized SortedMap<String, String> scan(String start, String end) {
        // Merge oldest-to-newest (SSTables in MANIFEST order, then the memtable last) so the
        // newest record for each key wins; null values (tombstones) shadow older live values.
        TreeMap<String, String> merged = new TreeMap<>();
        for (String name : manifest.sstables()) {
            merged.putAll(new SSTable(dataDir.resolve(name), mapper).entriesInRange(start, end));
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

    /** Flushes the memtable to a new SSTable. Stop-the-world: callers hold the monitor. */
    private void flush() {
        if (memtable.isEmpty()) {
            return;
        }
        String name = manifest.nextSstableName();
        SSTable.write(dataDir.resolve(name), memtable.entries(), mapper);
        manifest.append(name);
        memtable = new Memtable();
        wal.reset(); // the flushed writes are now durable in the SSTable
        if (manifest.sstables().size() >= compactionThreshold) {
            compact();
        }
    }

    /** Merges all SSTables into one, keeping the newest value per key and dropping tombstones. */
    private void compact() {
        List<String> sstables = manifest.sstables();
        if (sstables.size() <= 1) {
            return;
        }
        // Merge oldest-to-newest so later entries (puts or tombstones) overwrite earlier ones.
        Map<String, String> merged = new LinkedHashMap<>();
        for (String name : sstables) {
            merged.putAll(new SSTable(dataDir.resolve(name), mapper).entries());
        }
        merged.values().removeIf(value -> value == null); // drop tombstones

        String name = manifest.nextSstableName();
        SSTable.write(dataDir.resolve(name), merged, mapper);
        manifest.replaceAll(List.of(name));
        for (String old : sstables) {
            try {
                Files.deleteIfExists(dataDir.resolve(old));
            } catch (IOException e) {
                throw new UncheckedIOException("failed to delete compacted SSTable " + old, e);
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
        Set<String> referenced = new HashSet<>(manifest.sstables());
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
