package com.gladunalexander.keyvaluestorage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class HashInMemoryIndex implements InMemoryIndex {

    private final Map<Key, RecordMetadata> index = new HashMap<>();

    @Override
    public void put(Key key, RecordMetadata metadata) {
        index.put(key, metadata);
    }

    @Override
    public Optional<RecordMetadata> get(Key key) {
        return Optional.ofNullable(index.get(key));
    }
}
