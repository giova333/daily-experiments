package com.gladunalexander.keyvaluestorage;

import java.util.Optional;

public interface KeyValueStorage {

    void put(Key key, Value value);

    Optional<Value> get(Key key);

    static KeyValueStorage create() {
        return new DefaultKeyValueStorage();
    }

    void close();
}
