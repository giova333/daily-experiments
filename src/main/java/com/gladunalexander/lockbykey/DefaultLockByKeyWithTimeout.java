package com.gladunalexander.lockbykey;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class DefaultLockByKeyWithTimeout<T> implements LockByKeyWithTimeout<T> {

    private static final Duration UNLIMITED_DURATION = Duration.ofMillis(4133973600000L);
    private final Map<T, TimeoutableValue<T>> locks = new ConcurrentHashMap<>();

    @Override
    public boolean lock(T key) {
        return lock(key, UNLIMITED_DURATION);
    }

    @Override
    public boolean lock(T key, Duration duration) {
        var currentTimeMillis = System.currentTimeMillis();
        var timeoutableValue = new TimeoutableValue<>(key, currentTimeMillis + duration.toMillis());
        var currentValue = locks.compute(key, (k, v) -> {
            if (v == null || v.getExpiresAt() <= currentTimeMillis) return timeoutableValue;
            return v;
        });
        return timeoutableValue == currentValue;
    }

    @Override
    public void unlock(T key) {
        locks.remove(key);
    }
}
