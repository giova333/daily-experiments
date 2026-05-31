package com.gladunalexander.lsmkv.sstable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The MANIFEST records which SSTables exist, at which level, and over which key range.
 *
 * <p>It is stored as a JSON array of {@link SSTableMeta}, ordered so that within level 0
 * the newest SSTable is last. Updates are atomic (write a temp file, fsync, then rename) so
 * a crash can never leave a half-written MANIFEST.
 */
public class Manifest {

    private static final String FILE_NAME = "MANIFEST";
    private static final TypeReference<List<SSTableMeta>> LIST_TYPE = new TypeReference<>() {
    };

    private final Path path;
    private final ObjectMapper mapper;
    private final List<SSTableMeta> tables = new ArrayList<>();

    public Manifest(Path dataDir, ObjectMapper mapper) {
        this.path = dataDir.resolve(FILE_NAME);
        this.mapper = mapper;
        load();
    }

    private void load() {
        try {
            if (Files.notExists(path)) {
                Files.createFile(path);
                return;
            }
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length > 0) {
                tables.addAll(mapper.readValue(bytes, LIST_TYPE));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load MANIFEST at " + path, e);
        }
    }

    /** All SSTables, in MANIFEST order. */
    public List<SSTableMeta> all() {
        return List.copyOf(tables);
    }

    /** SSTables at the given level, oldest-first (so level 0 has the newest last). */
    public List<SSTableMeta> level(int level) {
        List<SSTableMeta> result = new ArrayList<>();
        for (SSTableMeta meta : tables) {
            if (meta.level() == level) {
                result.add(meta);
            }
        }
        return result;
    }

    public int maxLevel() {
        int max = 0;
        for (SSTableMeta meta : tables) {
            max = Math.max(max, meta.level());
        }
        return max;
    }

    /** The next unused SSTable id (numeric part of {@code sst-N.json}). */
    public int nextId() {
        int maxId = 0;
        for (SSTableMeta meta : tables) {
            maxId = Math.max(maxId, idOf(meta.filename()));
        }
        return maxId + 1;
    }

    /** Records a newly written SSTable, atomically. */
    public void add(SSTableMeta meta) {
        tables.add(meta);
        rewriteAtomically();
    }

    /** Replaces the entire SSTable set (used by compaction) and persists it atomically. */
    public void replaceAll(List<SSTableMeta> newTables) {
        tables.clear();
        tables.addAll(newTables);
        rewriteAtomically();
    }

    private void rewriteAtomically() {
        Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
        try {
            byte[] bytes = mapper.writeValueAsBytes(tables);
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ch.write(ByteBuffer.wrap(bytes));
                ch.force(true); // fsync the new contents before swapping it in
            }
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to rewrite MANIFEST", e);
        }
    }

    private static int idOf(String sstableName) {
        try {
            return Integer.parseInt(sstableName.replace("sst-", "").replace(".json", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
