package com.gladunalexander.lockbykey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-key blocking lock. Entries are reference counted and the count is only touched inside
 * {@link ConcurrentHashMap} atomic operations, so an entry can never be evicted while a thread
 * is about to wait on it. Blocking happens outside the map, so contention on one key does not
 * hold a map bin lock. Memory use is proportional to the number of keys currently in use.
 */
public class RefCountedLockByKey<T> implements LockByKey<T> {

    private static final class Entry {
        final ReentrantLock lock = new ReentrantLock();
        int refs; // guarded by the map's per-key atomicity
    }

    private final Map<T, Entry> locks = new ConcurrentHashMap<>();

    @Override
    public boolean lock(T key) {
        var entry = locks.compute(key, (k, e) -> {
            if (e == null) e = new Entry();
            e.refs++;
            return e;
        });
        entry.lock.lock();
        return true;
    }

    @Override
    public void unlock(T key) {
        locks.computeIfPresent(key, (k, e) -> {
            e.lock.unlock(); // throws if the caller does not hold the lock; refs stay intact
            return --e.refs == 0 ? null : e;
        });
    }

    int size() {
        return locks.size();
    }
}
