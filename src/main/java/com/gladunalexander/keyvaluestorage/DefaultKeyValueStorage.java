package com.gladunalexander.keyvaluestorage;

import lombok.SneakyThrows;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import static io.vavr.API.unchecked;
import static java.util.function.Predicate.not;

public class DefaultKeyValueStorage implements KeyValueStorage {

    private final ReentrantLock writeLock;
    private final InMemoryIndex index;
    private final DataFile dataFile;

    @SneakyThrows
    public DefaultKeyValueStorage() {
        writeLock = new ReentrantLock();
        index = new PersistentHashIndex();
        dataFile = new DataFile();
    }

    @Override
    public void put(Key key, Value value) {
        put(key, value, -1);
    }

    @Override
    public void put(Key key, Value value, Duration ttl) {
        var ttlTimestamp = System.currentTimeMillis() + ttl.toMillis();
        put(key, value, ttlTimestamp);
    }

    @Override
    public Optional<Value> get(Key key) {
        return index.get(key)
                    .map(unchecked(dataFile::read))
                    .filter(not(Record::isTombstone))
                    .filter(not(Record::isExpired))
                    .map(Record::getValue);
    }

    @Override
    public void delete(Key key) {
        index.get(key).ifPresent(__ -> put(key, Value.empty()));
    }

    @Override
    @SneakyThrows
    public void close() {
        dataFile.close();
        index.destroy();
    }

    @SneakyThrows
    private void put(Key key, Value value, long ttl) {
        writeLock.lock();
        try {
            var recordMetadata = dataFile.write(key, value, ttl);
            index.put(key, recordMetadata);
        } finally {
            writeLock.unlock();
        }
    }

}
