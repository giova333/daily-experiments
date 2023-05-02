package com.gladunalexander.distributedsystems.versionedvalue;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

public class MVCCStore<K extends Comparable<K>, V> {

    private final NavigableMap<VersionedKey<K>, V> kv = new ConcurrentSkipListMap<>();
    private final AtomicLong version = new AtomicLong();

    public long put(K key, V value) {
        var currentVersion = version.incrementAndGet();
        var versionedKey = new VersionedKey<>(key, currentVersion);
        kv.put(versionedKey, value);
        return currentVersion;
    }

    public Optional<V> get(K key, long version) {
        var entry = kv.floorEntry(new VersionedKey<>(key, version));
        return Optional.ofNullable(entry).map(Map.Entry::getValue);
    }

}
