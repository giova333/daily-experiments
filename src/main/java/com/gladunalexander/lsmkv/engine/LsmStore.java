package com.gladunalexander.lsmkv.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gladunalexander.lsmkv.sstable.Manifest;
import com.gladunalexander.lsmkv.sstable.SSTable;
import com.gladunalexander.lsmkv.wal.Wal;

import jakarta.annotation.PreDestroy;

/**
 * Week 2: the LSM-tree engine. Week 3 adds durability.
 *
 * <p>Writes are appended to a {@link Wal} (and fsynced) <em>before</em> being applied to
 * the in-memory {@link Memtable}, so acknowledged writes survive a crash. When the
 * memtable exceeds a threshold it is flushed, stop-the-world, into an immutable SSTable;
 * the WAL is then reset since its contents are now durable in the SSTable.
 *
 * <p>On startup the engine deletes any dangling SSTable not listed in the {@link Manifest}
 * (a crash between writing the SSTable and updating the MANIFEST) and replays the WAL to
 * rebuild the memtable.
 *
 * <p>Reads consult the memtable first, then SSTables newest-to-oldest.
 */
@Component
@Primary
public class LsmStore implements Store {

    private final Path dataDir;
    private final ObjectMapper mapper;
    private final int memtableMaxEntries;

    private final Manifest manifest;
    private final Wal wal;
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
        deleteDanglingSstables();
        this.wal = new Wal(this.dataDir);
        recoverFromWal();
    }

    @Override
    public synchronized void put(String key, String value) {
        wal.append(key, value); // durable before we acknowledge
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
        wal.reset(); // the flushed writes are now durable in the SSTable
    }

    private void recoverFromWal() {
        for (Map.Entry<String, String> e : wal.replay().entrySet()) {
            memtable.put(e.getKey(), e.getValue());
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
