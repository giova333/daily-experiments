package com.gladunalexander.keyvaluestorage;

import lombok.SneakyThrows;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PersistentHashIndex implements InMemoryIndex {

    private final Map<Key, RecordMetadata> index = new HashMap<>();
    private final IndexFile indexFile;

    public PersistentHashIndex() throws IOException {
        indexFile = new IndexFile();
        populateIndex();
    }

    @Override
    @SneakyThrows
    public void put(Key key, RecordMetadata metadata) {
        index.put(key, metadata);
        indexFile.write(key, metadata);
    }

    @Override
    public Optional<RecordMetadata> get(Key key) {
        return Optional.ofNullable(index.get(key));
    }

    @Override
    @SneakyThrows
    public void destroy() {
        indexFile.close();
    }

    private void populateIndex() {
        indexFile.forEach(entry -> index.put(entry.getKey(), entry.getRecordMetadata()));
    }

}
