package com.gladunalexander.keyvaluestorage;

import java.util.Optional;

public interface InMemoryIndex {
    void put(Key key, RecordMetadata metadata);

    Optional<RecordMetadata> get(Key key);
}
