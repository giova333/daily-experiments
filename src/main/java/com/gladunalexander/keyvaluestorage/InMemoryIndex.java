package com.gladunalexander.keyvaluestorage;

import java.util.Optional;

public interface InMemoryIndex {
    void put(Key key, ValueMetadata metadata);

    Optional<ValueMetadata> get(Key key);
}
