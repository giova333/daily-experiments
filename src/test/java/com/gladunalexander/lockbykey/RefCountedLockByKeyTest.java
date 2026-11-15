package com.gladunalexander.lockbykey;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RefCountedLockByKeyTest {

    @Test
    void providesMutualExclusionPerKeyAndCleansUp() throws Exception {
        var locks = new RefCountedLockByKey<Integer>();
        var counters = new int[4];
        var pool = Executors.newFixedThreadPool(8);
        for (var t = 0; t < 8; t++) {
            pool.submit(() -> {
                for (var i = 0; i < 20_000; i++) {
                    var key = i % 4;
                    locks.lock(key);
                    try {
                        counters[key]++;
                    } finally {
                        locks.unlock(key);
                    }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        for (var c : counters) assertEquals(8 * 5_000, c);
        assertEquals(0, locks.size());
    }

    @Test
    void unlockByNonOwnerThrowsAndKeepsState() {
        var locks = new RefCountedLockByKey<String>();
        locks.unlock("missing");
        assertEquals(0, locks.size());
        locks.lock("b");
        var failure = new Throwable[1];
        var t = new Thread(() -> {
            try {
                locks.unlock("b");
            } catch (Throwable e) {
                failure[0] = e;
            }
        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        assertEquals(IllegalMonitorStateException.class, failure[0].getClass());
        assertEquals(1, locks.size());
        locks.unlock("b");
        assertEquals(0, locks.size());
    }
}
