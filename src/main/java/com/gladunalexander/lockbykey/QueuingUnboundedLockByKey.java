package com.gladunalexander.lockbykey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class QueuingUnboundedLockByKey<T> implements LockByKey<T> {

    private final Map<T, LockHolder> locks = new ConcurrentHashMap<>();

    @Override
    public boolean lock(T key) {
        var lockHolder = locks.computeIfAbsent(key, k -> new LockHolder());
        lockHolder.lock();
        return true;
    }

    @Override
    public void unlock(T key) {
        locks.computeIfPresent(key, (k, v) -> {
            var waitingThreads = v.unlock();
            if (waitingThreads == 0) return null;
            return v;
        });
    }
}
