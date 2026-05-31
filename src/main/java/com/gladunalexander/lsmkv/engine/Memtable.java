package com.gladunalexander.lsmkv.engine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.gladunalexander.lsmkv.sstable.Slot;
import com.gladunalexander.lsmkv.trie.RadixTrie;

/**
 * The in-memory write buffer of the LSM tree.
 *
 * <p>Week 7 backs it with a radix trie (replacing the TreeMap). The trie keeps keys in
 * sorted order, so flushes stay sorted and range scans come naturally, while sharing key
 * prefixes in memory. A {@code null} value is a tombstone.
 */
public class Memtable {

    private final RadixTrie trie = new RadixTrie();

    public void put(String key, String value) {
        trie.put(key, value);
    }

    public void delete(String key) {
        trie.put(key, null); // tombstone
    }

    /** Looks the key up, distinguishing a live value, a tombstone, and absence. */
    public Optional<Slot> lookup(String key) {
        if (!trie.contains(key)) {
            return Optional.empty();
        }
        return trie.lookup(key)
                .map(Slot::of)
                .or(() -> Optional.of(Slot.deleted())); // present but null => tombstone
    }

    public int size() {
        return trie.size();
    }

    public boolean isEmpty() {
        return trie.isEmpty();
    }

    /** Snapshot of all entries (sorted by key, {@code null} = tombstone) for flushing. */
    public LinkedHashMap<String, String> entries() {
        return trie.entries();
    }

    /** Entries whose key is within {@code [start, end]} (inclusive), sorted by key. */
    public LinkedHashMap<String, String> rangeEntries(String start, String end) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : trie.entries().entrySet()) {
            if (e.getKey().compareTo(start) >= 0 && e.getKey().compareTo(end) <= 0) {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }
}
