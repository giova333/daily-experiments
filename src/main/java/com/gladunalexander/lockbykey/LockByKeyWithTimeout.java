package com.gladunalexander.lockbykey;

import java.time.Duration;

interface LockByKeyWithTimeout<T> extends LockByKey<T> {
    boolean lock(T key, Duration duration);
}
