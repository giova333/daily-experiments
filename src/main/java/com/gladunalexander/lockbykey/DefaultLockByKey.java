package com.gladunalexander.lockbykey;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class DefaultLockByKey<T> implements LockByKey<T> {

    private final Set<T> locks = ConcurrentHashMap.newKeySet();

    @Override
    public boolean lock(T key) {
        return locks.add(key);
    }

    @Override
    public void unlock(T key) {
        locks.remove(key);
    }
}
