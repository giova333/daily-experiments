package com.gladunalexander.lsmkv.engine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.gladunalexander.lsmkv.sstable.Slot;

/**
 * The in-memory write buffer of the LSM tree.
 *
 * <p>Backed by a hashtable mapping key to value, where a {@code null} value is a tombstone
 * (a logical delete). Week 5 swaps this for an ordered structure to support range scans.
 */
public class Memtable {

    private final Map<String, String> entries = new LinkedHashMap<>();

    public void put(String key, String value) {
        entries.put(key, value);
    }

    public void delete(String key) {
        entries.put(key, null); // tombstone
    }

    /** Looks the key up, distinguishing a live value, a tombstone, and absence. */
    public Optional<Slot> lookup(String key) {
        if (!entries.containsKey(key)) {
            return Optional.empty();
        }
        String value = entries.get(key);
        return Optional.of(value == null ? Slot.deleted() : Slot.of(value));
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Snapshot of the entries (key to value, {@code null} = tombstone) for flushing. */
    public Map<String, String> entries() {
        return entries;
    }
}
