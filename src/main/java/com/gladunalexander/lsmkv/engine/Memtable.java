package com.gladunalexander.lsmkv.engine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The in-memory write buffer of the LSM tree.
 *
 * <p>Week 2 backs it with a plain hashtable. A later week swaps this for an ordered
 * structure (to support range scans) and eventually a trie.
 */
public class Memtable {

    private final Map<String, String> entries = new LinkedHashMap<>();

    public void put(String key, String value) {
        entries.put(key, value);
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(entries.get(key));
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Returns a snapshot view of the entries (key insertion order). */
    public Map<String, String> entries() {
        return entries;
    }

    public void clear() {
        entries.clear();
    }
}
