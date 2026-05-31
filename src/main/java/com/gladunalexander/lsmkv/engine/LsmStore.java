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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
 * The LSM-tree engine. Week 8 removes the stop-the-world model so reads and writes proceed
 * concurrently with flushes and compactions.
 *
 * <p>A {@link ReentrantReadWriteLock} guards the in-memory state: writers take the write
 * lock to append to the WAL and the active memtable; readers take the read lock. When the
 * active memtable fills, it is rotated to an immutable {@code flushing} memtable (and the
 * WAL is segmented) and a background thread flushes it. The flush and any follow-on
 * compaction do their heavy IO <em>without</em> holding the lock, taking the write lock only
 * for the brief commit (updating the MANIFEST and clearing the flushing memtable), so reads
 * are never blocked for the duration of the IO.
 *
 * <p>Flush and compaction share a single background thread, so they never race each other,
 * and the background thread is the only mutator of on-disk SSTables.
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
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lsmkv-background");
        t.setDaemon(true);
        return t;
    });

    private Memtable active = new Memtable();
    private Memtable flushing; // immutable snapshot being flushed, or null

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
    public void put(String key, String value) {
        lock.writeLock().lock();
        try {
            wal.appendPut(key, value); // durable before we acknowledge
            active.put(key, value);
            maybeRotate();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void delete(String key) {
        lock.writeLock().lock();
        try {
            wal.appendDelete(key);
            active.delete(key); // tombstone
            maybeRotate();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<String> get(String key) {
        lock.readLock().lock();
        try {
            Optional<Slot> hit = active.lookup(key);
            if (hit.isEmpty() && flushing != null) {
                hit = flushing.lookup(key);
            }

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
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public SortedMap<String, String> scan(String start, String end) {
        lock.readLock().lock();
        try {
            // Merge oldest-to-newest so the newest record per key wins.
            TreeMap<String, String> merged = new TreeMap<>();
            for (int n = manifest.maxLevel(); n >= 1; n--) {
                for (SSTableMeta meta : manifest.level(n)) {
                    merged.putAll(sstable(meta).entriesInRange(start, end));
                }
            }
            for (SSTableMeta meta : manifest.level(0)) {
                merged.putAll(sstable(meta).entriesInRange(start, end));
            }
            if (flushing != null) {
                merged.putAll(flushing.rangeEntries(start, end));
            }
            merged.putAll(active.rangeEntries(start, end));
            merged.values().removeIf(value -> value == null); // exclude deleted keys
            return merged;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Rotates the active memtable for background flushing. Caller holds the write lock. */
    private void maybeRotate() {
        if (active.size() < memtableMaxEntries || flushing != null) {
            return; // not full yet, or a flush is still in progress (keep one segment max)
        }
        Memtable toFlush = active;
        flushing = toFlush;
        active = new Memtable();
        wal.rotate(); // seal the segment belonging to toFlush, start a fresh one for active
        background.submit(() -> flushAndCompact(toFlush));
    }

    /** Background: write the rotated memtable to an SSTable, then run leveled compaction. */
    private void flushAndCompact(Memtable toFlush) {
        try {
            Map<String, String> entries = toFlush.entries(); // sorted, immutable now
            String minKey = null;
            String maxKey = null;
            for (String key : entries.keySet()) {
                if (minKey == null) {
                    minKey = key;
                }
                maxKey = key;
            }
            int id = nextId();
            String name = "sst-" + id + ".json";
            SSTable.write(dataDir.resolve(name), entries); // heavy IO, no lock held

            lock.writeLock().lock();
            try {
                manifest.add(new SSTableMeta(name, 0, minKey, maxKey));
                flushing = null;
                wal.discardOld(); // the flushed writes are now durable in the SSTable
            } finally {
                lock.writeLock().unlock();
            }

            maybeCompact();
        } catch (RuntimeException e) {
            // Background failures must not silently strand the flushing memtable.
            System.err.println("lsmkv background flush failed: " + e);
        }
    }

    /** Runs leveled compaction until every level is within its budget. Background thread only. */
    private void maybeCompact() {
        while (true) {
            int target;
            lock.readLock().lock();
            try {
                target = pickCompactionLevel();
            } finally {
                lock.readLock().unlock();
            }
            if (target < 0) {
                return;
            }
            compact(target);
        }
    }

    private int pickCompactionLevel() {
        if (manifest.level(0).size() >= l0Trigger) {
            return 0;
        }
        for (int n = 1; n <= manifest.maxLevel(); n++) {
            if (manifest.level(n).size() > budget(n)) {
                return n;
            }
        }
        return -1;
    }

    /** Merges level {@code n} into level {@code n + 1}, rebuilding the latter non-overlapping. */
    private void compact(int n) {
        List<SSTableMeta> lower;
        List<SSTableMeta> upper;
        boolean targetIsBottom;
        lock.readLock().lock();
        try {
            lower = manifest.level(n);
            upper = manifest.level(n + 1);
            targetIsBottom = manifest.maxLevel() <= n + 1;
        } finally {
            lock.readLock().unlock();
        }

        // Merge oldest-to-newest (heavy IO, no lock): upper level first, then the lower level.
        TreeMap<String, String> merged = new TreeMap<>();
        for (SSTableMeta meta : upper) {
            merged.putAll(sstable(meta).entries());
        }
        for (SSTableMeta meta : lower) {
            merged.putAll(sstable(meta).entries());
        }
        if (targetIsBottom) {
            merged.values().removeIf(value -> value == null); // drop tombstones at the bottom
        }
        List<SSTableMeta> rebuilt = partitionIntoSstables(merged, n + 1);

        lock.writeLock().lock();
        try {
            List<SSTableMeta> kept = new ArrayList<>();
            for (SSTableMeta meta : manifest.all()) {
                if (meta.level() != n && meta.level() != n + 1) {
                    kept.add(meta);
                }
            }
            kept.addAll(rebuilt);
            manifest.replaceAll(kept);
        } finally {
            lock.writeLock().unlock();
        }

        deleteFiles(lower);
        deleteFiles(upper);
    }

    /** Splits sorted entries into non-overlapping SSTables of at most {@code memtableMaxEntries}. */
    private List<SSTableMeta> partitionIntoSstables(SortedMap<String, String> entries, int level) {
        List<SSTableMeta> result = new ArrayList<>();
        int nextId = nextId();
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

    private int nextId() {
        lock.readLock().lock();
        try {
            return manifest.nextId();
        } finally {
            lock.readLock().unlock();
        }
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
        for (Map.Entry<String, String> e : wal.replayAll().entrySet()) {
            if (e.getValue() == null) {
                active.delete(e.getKey());
            } else {
                active.put(e.getKey(), e.getValue());
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
    public void close() {
        background.shutdown();
        try {
            if (!background.awaitTermination(30, TimeUnit.SECONDS)) {
                background.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        wal.close();
    }
}
