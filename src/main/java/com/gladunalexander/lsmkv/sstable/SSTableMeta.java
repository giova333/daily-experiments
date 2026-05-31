package com.gladunalexander.lsmkv.sstable;

/**
 * MANIFEST metadata for one SSTable: its file name, the level it lives at, and the
 * inclusive key range it covers.
 *
 * <p>Level 0 SSTables come straight from flushes and may overlap. From level 1 down, the
 * SSTables within a level are non-overlapping, so a key maps to at most one SSTable per
 * level — which the read path uses to skip straight to the relevant file.
 */
public record SSTableMeta(String filename, int level, String minKey, String maxKey) {

    /** True if the given key falls within this SSTable's inclusive key range. */
    public boolean covers(String key) {
        return key.compareTo(minKey) >= 0 && key.compareTo(maxKey) <= 0;
    }
}
