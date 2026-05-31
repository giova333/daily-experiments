package com.gladunalexander.lsmkv.engine;

import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import com.gladunalexander.lsmkv.sstable.Slot;

/**
 * The in-memory write buffer of the LSM tree.
 *
 * <p>Backed by an ordered map (key to value, {@code null} = tombstone) so keys can be
 * iterated in sorted order. This both keeps flushed SSTables sorted and supports range
 * scans. Week 7 swaps this for a trie with the same ordering guarantees.
 */
public class Memtable {

    private final TreeMap<String, String> entries = new TreeMap<>();

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

    /** Snapshot of all entries (sorted by key, {@code null} = tombstone) for flushing. */
    public SortedMap<String, String> entries() {
        return entries;
    }

    /** Entries whose key is within {@code [start, end]} (inclusive), sorted by key. */
    public SortedMap<String, String> rangeEntries(String start, String end) {
        return new TreeMap<>(entries.subMap(start, true, end, true));
    }
}
