package com.gladunalexander.distributedsystems.wal;

import lombok.Builder;
import lombok.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class KVStore {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, String> kv = new HashMap<>();
    private final WAL wal;

    private KVStore(WAL wal) {
        this.wal = wal;
        applyWAL();
    }

    public static KVStore from(Configuration configuration) {
        return new KVStore(WAL.openWal(configuration));
    }

    public void set(String key, String value) {
        lock.writeLock().lock();
        try {
            wal.writeEntry(SetValueCommand.of(key, value));
            kv.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void del(String key) {
        lock.writeLock().lock();
        try {
            wal.writeEntry(DelValueCommand.of(key));
            kv.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String get(String key) {
        return kv.get(key);
    }

    private void applyWAL() {
        lock.readLock().lock();
        try {
            wal.readWALEntries().forEach(this::apply);
        } finally {
            lock.readLock().unlock();
        }

    }

    private void apply(WALCommand command) {
        switch (command.type()) {
            case SET:
                var setValueCommand = (SetValueCommand) command;
                kv.put(setValueCommand.getKey(), setValueCommand.getValue());
                break;
            case DEL:
                var delValueCommand = (DelValueCommand) command;
                kv.remove(delValueCommand.getKey());
                break;
            default:
                throw new IllegalArgumentException("Invalid command");
        }
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", KVStore.class.getSimpleName() + "[", "]")
                .add("kv=" + kv)
                .toString();
    }

    @Value
    @Builder
    public static class Configuration {
        String walFileName;
    }

}
