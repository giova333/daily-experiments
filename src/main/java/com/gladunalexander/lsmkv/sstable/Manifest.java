package com.gladunalexander.lsmkv.sstable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The MANIFEST is an append-only list of SSTable filenames. It records which SSTables
 * exist and in which order to read them. The file is ordered oldest-first, so the
 * newest SSTable is the last line.
 *
 * <p>Week 2: a simple newline-delimited text file. Week 3 makes updates atomic and adds
 * a startup routine that deletes any on-disk SSTable not listed here.
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

    /** Records a newly written SSTable as the newest entry. */
    public void append(String sstableName) {
        try {
            Files.writeString(path, sstableName + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            sstables.add(sstableName);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to append to MANIFEST", e);
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
