package com.gladunalexander.threadpool;

public class Test {

    public static void main(String[] args) {
        var threadPool = new ThreadPool(3);

        threadPool.submit(task());
        threadPool.submit(task());
        threadPool.submit(task());
        threadPool.submit(task());

        threadPool.shutdown();
    }

    private static Runnable task() {
        return () -> System.out.println("Executing");
    }
}
