package com.gladunalexander.lockbykey;

interface LockByKey<T> {
    boolean lock(T key);

    void unlock(T key);
}
