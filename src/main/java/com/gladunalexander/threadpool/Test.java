package com.gladunalexander.threadpool;

import java.time.Duration;
import java.util.concurrent.Callable;

public class Test {

    public static void main(String[] args) {
        var threadPool = new ThreadPool(3);

        var future = threadPool.submit(task());

        System.out.println(future.getAwaiting(Duration.ofMillis(3300)));
    }

    private static Callable<String> task() {
        return () -> {
            Thread.sleep(3000);
            return "Hello";
        };
    }
}
