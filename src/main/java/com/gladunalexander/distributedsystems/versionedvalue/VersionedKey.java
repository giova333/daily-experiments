package com.gladunalexander.distributedsystems.versionedvalue;

import lombok.Getter;

@Getter
public class VersionedKey<K extends Comparable<K>> implements Comparable<VersionedKey<K>> {
    private final K key;
    private final long version;

    public VersionedKey(K key, long version) {
        this.key = key;
        this.version = version;
    }

    @Override
    public int compareTo(VersionedKey<K> o) {
        var compareResult = this.key.compareTo(o.key);
        return compareResult != 0
                ? compareResult
                : Long.compare(this.version, o.version);
    }
}
