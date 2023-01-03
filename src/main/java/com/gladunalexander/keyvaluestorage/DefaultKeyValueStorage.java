package com.gladunalexander.keyvaluestorage;

import lombok.SneakyThrows;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import static io.vavr.API.unchecked;

public class DefaultKeyValueStorage implements KeyValueStorage {

    private final ReentrantLock writeLock;
    private final InMemoryIndex index;
    private final StorageFile file;

    @SneakyThrows
    public DefaultKeyValueStorage() {
        writeLock = new ReentrantLock();
        index = new HashInMemoryIndex();
        file = new StorageFile();
    }

    @Override
    @SneakyThrows
    public void put(Key key, Value value) {
        writeLock.lock();
        try {
            var recordMetadata = file.write(key, value);
            index.put(key, recordMetadata);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Optional<Value> get(Key key) {
        return index.get(key)
                    .map(unchecked(file::read))
                    .map(Record::getValue);
    }

    @Override
    @SneakyThrows
    public void close() {
        file.close();
    }

}
