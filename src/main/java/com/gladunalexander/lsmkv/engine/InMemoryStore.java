package com.gladunalexander.lsmkv.engine;

import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Week 1: the simplest possible store. Everything is kept in memory in a hashtable.
 *
 * <p>Nothing is persisted, so all data is lost on restart. Later weeks replace this
 * with an LSM-tree based engine that survives restarts.
 */
@Component
public class InMemoryStore implements Store {

    private final Map<String, String> data = new ConcurrentHashMap<>();

    @Override
    public void put(String key, String value) {
        data.put(key, value);
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(data.get(key));
    }

    @Override
    public void delete(String key) {
        data.remove(key);
    }

    @Override
    public SortedMap<String, String> scan(String start, String end) {
        SortedMap<String, String> result = new TreeMap<>();
        for (Map.Entry<String, String> e : data.entrySet()) {
            if (e.getKey().compareTo(start) >= 0 && e.getKey().compareTo(end) <= 0) {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }
}
