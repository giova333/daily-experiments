package com.gladunalexander.lockbykey;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class LockHolder {
    private final Lock lock = new ReentrantLock();
    private final AtomicInteger queue = new AtomicInteger();

    public void lock() {
        queue.incrementAndGet();
        lock.lock();
    }

    public int unlock() {
        lock.unlock();
        return queue.decrementAndGet();
    }
}
