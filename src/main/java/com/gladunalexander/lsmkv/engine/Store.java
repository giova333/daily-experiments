package com.gladunalexander.lsmkv.engine;

import java.util.Optional;
import java.util.SortedMap;

/**
 * The key-value storage engine contract.
 *
 * <p>Keys are lowercase ASCII strings, values are ASCII strings. The contract is
 * intentionally small and stable; later weeks of the series grow it (delete in
 * week 4, scan in week 5) but the core stays the same.
 */
public interface Store {

    /**
     * Stores a key-value pair. If the key already exists its value is overwritten.
     */
    void put(String key, String value);

    /**
     * Returns the value associated with the key, or empty if the key is absent or deleted.
     */
    Optional<String> get(String key);

    /**
     * Deletes a key. Subsequent gets return empty until the key is put again.
     */
    void delete(String key);

    /**
     * Returns the live key-value pairs whose key is within {@code [start, end]} (inclusive),
     * sorted by key. Deleted keys are excluded.
     */
    SortedMap<String, String> scan(String start, String end);
}
