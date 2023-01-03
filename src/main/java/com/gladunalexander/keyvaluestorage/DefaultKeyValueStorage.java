package com.gladunalexander.keyvaluestorage;

import lombok.SneakyThrows;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import static io.vavr.API.unchecked;

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
    @SneakyThrows
    public void put(Key key, Value value) {
        writeLock.lock();
        try {
            var recordMetadata = dataFile.write(key, value);
            index.put(key, recordMetadata);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Optional<Value> get(Key key) {
        return index.get(key)
                    .map(unchecked(dataFile::read))
                    .map(Record::getValue);
    }

    @Override
    @SneakyThrows
    public void close() {
        dataFile.close();
        index.destroy();
    }

}
