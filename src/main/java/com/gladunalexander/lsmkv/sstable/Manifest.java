package com.gladunalexander.lsmkv.sstable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The MANIFEST is an append-only list of SSTable filenames. It records which SSTables
 * exist and in which order to read them. The file is ordered oldest-first, so the
 * newest SSTable is the last line.
 *
 * <p>Week 3 makes updates atomic (write a temp file, fsync, then atomically rename) so a
 * crash can never leave a half-written MANIFEST.
 */
public class Manifest {

    private static final String FILE_NAME = "MANIFEST";

    private final Path path;
    private final List<String> sstables = new ArrayList<>();

    public Manifest(Path dataDir) {
        this.path = dataDir.resolve(FILE_NAME);
        load();
    }

    private void load() {
        try {
            if (Files.notExists(path)) {
                Files.createFile(path);
                return;
            }
            for (String line : Files.readAllLines(path)) {
                if (!line.isBlank()) {
                    sstables.add(line.trim());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load MANIFEST at " + path, e);
        }
    }

    /** SSTable filenames, oldest first. */
    public List<String> sstables() {
        return List.copyOf(sstables);
    }

    /** Derives the next SSTable filename so we never reuse an id, even across restarts. */
    public String nextSstableName() {
        int maxId = 0;
        for (String name : sstables) {
            maxId = Math.max(maxId, idOf(name));
        }
        return "sst-" + (maxId + 1) + ".json";
    }

    /** Records a newly written SSTable as the newest entry, atomically. */
    public void append(String sstableName) {
        sstables.add(sstableName);
        rewriteAtomically();
    }

    /** Replaces the SSTable list (used by compaction) and persists it atomically. */
    public void replaceAll(List<String> newSstables) {
        sstables.clear();
        sstables.addAll(newSstables);
        rewriteAtomically();
    }

    private void rewriteAtomically() {
        StringBuilder sb = new StringBuilder();
        for (String name : sstables) {
            sb.append(name).append('\n');
        }
        Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
        try {
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ch.write(ByteBuffer.wrap(sb.toString().getBytes(StandardCharsets.UTF_8)));
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
