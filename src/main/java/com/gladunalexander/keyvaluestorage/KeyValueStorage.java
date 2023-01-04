package com.gladunalexander.keyvaluestorage;

import java.util.Optional;

public interface KeyValueStorage {

    void put(Key key, Value value);

    Optional<Value> get(Key key);

    void delete(Key key);

    void close();
}
