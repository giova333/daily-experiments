package com.gladunalexander.lockbykey;

import java.util.concurrent.locks.ReentrantLock;

public class QueuingBoundedLockByKey<T> implements LockByKey<T> {

    private final int size;
    private final ReentrantLock[] locks;

    public QueuingBoundedLockByKey(int size) {
        this.size = size;
        this.locks = new ReentrantLock[size];
        for (var i = 0; i < size; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    @Override
    public boolean lock(T key) {
        getLock(key).lock();
        return true;
    }

    @Override
    public void unlock(T key) {
        getLock(key).unlock();
    }

    private ReentrantLock getLock(T key) {
        return locks[key.hashCode() % size];
    }
}
