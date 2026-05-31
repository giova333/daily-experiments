package com.gladunalexander.lsmkv.sstable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A Sorted String Table: an immutable, on-disk snapshot of a flushed memtable.
 *
 * <p>Week 2 uses a simple JSON object ({@code {"k":"v", ...}}) as the on-disk format
 * and a linear lookup inside the file. The format becomes block-based with a sparse
 * index and Bloom filter in week 7.
 */
public class SSTable {

    private static final TypeReference<LinkedHashMap<String, String>> MAP_TYPE =
            new TypeReference<>() {
            };

    private final Path file;
    private final ObjectMapper mapper;

    public SSTable(Path file, ObjectMapper mapper) {
        this.file = file;
        this.mapper = mapper;
    }

    /** Writes the given entries to a new SSTable file and fsyncs it to disk. */
    public static void write(Path file, Map<String, String> entries, ObjectMapper mapper) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(entries);
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ch.write(ByteBuffer.wrap(bytes));
                ch.force(true); // fsync so the SSTable is durable before the MANIFEST names it
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write SSTable " + file, e);
        }
    }

    /**
     * Looks up a key in this SSTable. Returns empty if the key is absent here; returns a
     * tombstone {@link Slot} if the key was deleted (stored as a {@code null} value).
     */
    public Optional<Slot> lookup(String key) {
        Map<String, String> data = load();
        if (!data.containsKey(key)) {
            return Optional.empty();
        }
        String value = data.get(key);
        return Optional.of(value == null ? Slot.deleted() : Slot.of(value));
    }

    /** All entries in this SSTable (key to value, {@code null} = tombstone). Used by compaction. */
    public Map<String, String> entries() {
        return load();
    }

    private Map<String, String> load() {
        try {
            if (Files.notExists(file)) {
                return Map.of();
            }
            return mapper.readValue(file.toFile(), MAP_TYPE);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read SSTable " + file, e);
        }
    }
}
