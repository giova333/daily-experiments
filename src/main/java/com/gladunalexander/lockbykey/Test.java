package com.gladunalexander.lockbykey;

public class Test {

    public static void main(String[] args) {
        var lockByKey = new DefaultLockByKey<>();

        System.out.println(lockByKey.lock(1));
        System.out.println(lockByKey.lock(2));
        System.out.println(lockByKey.lock(3));
        System.out.println(lockByKey.lock(1));

        lockByKey.unlock(1);
        lockByKey.unlock(2);
        System.out.println(lockByKey.lock(1));
    }
}
