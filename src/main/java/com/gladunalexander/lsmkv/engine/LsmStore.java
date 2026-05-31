package com.gladunalexander.lsmkv.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gladunalexander.lsmkv.sstable.Manifest;
import com.gladunalexander.lsmkv.sstable.SSTable;

/**
 * Week 2: the LSM-tree engine.
 *
 * <p>Writes land in an in-memory {@link Memtable}. When the memtable grows past a
 * threshold it is flushed, as a stop-the-world operation, into a new immutable SSTable
 * file and recorded in the {@link Manifest}.
 *
 * <p>Reads consult the memtable first, then the SSTables from newest to oldest, stopping
 * at the first hit.
 *
 * <p>This is the primary {@link Store}; {@link InMemoryStore} remains only as the week-1
 * artifact.
 */
@Component
@Primary
public class LsmStore implements Store {

    private final Path dataDir;
    private final ObjectMapper mapper;
    private final int memtableMaxEntries;

    private final Manifest manifest;
    private Memtable memtable = new Memtable();

    public LsmStore(@Value("${lsmkv.data-dir:lsmkv-data}") String dataDir,
                    @Value("${lsmkv.memtable-max-entries:1024}") int memtableMaxEntries,
                    ObjectMapper mapper) {
        this.dataDir = Path.of(dataDir);
        this.mapper = mapper;
        this.memtableMaxEntries = memtableMaxEntries;
        try {
            Files.createDirectories(this.dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create data dir " + dataDir, e);
        }
        this.manifest = new Manifest(this.dataDir);
    }

    @Override
    public synchronized void put(String key, String value) {
        memtable.put(key, value);
        if (memtable.size() >= memtableMaxEntries) {
            flush();
        }
    }

    @Override
    public synchronized Optional<String> get(String key) {
        Optional<String> fromMemtable = memtable.get(key);
        if (fromMemtable.isPresent()) {
            return fromMemtable;
        }
        // Newest SSTable wins, so scan the manifest from newest (last) to oldest (first).
        List<String> sstables = manifest.sstables();
        for (int i = sstables.size() - 1; i >= 0; i--) {
            SSTable sstable = new SSTable(dataDir.resolve(sstables.get(i)), mapper);
            Optional<String> value = sstable.get(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
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
    }
}
