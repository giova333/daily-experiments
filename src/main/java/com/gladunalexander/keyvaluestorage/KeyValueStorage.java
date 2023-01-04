package com.gladunalexander.keyvaluestorage;

import java.time.Duration;
import java.util.Optional;

public interface KeyValueStorage {

    void put(Key key, Value value);

    void put(Key key, Value value, Duration ttl);

    Optional<Value> get(Key key);

    void delete(Key key);

    void close();
}
