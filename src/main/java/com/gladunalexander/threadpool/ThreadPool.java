package com.gladunalexander.threadpool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

public class ThreadPool {

    private final Thread[] pool;
    private final BlockingQueue<Future<?>> tasks = new LinkedBlockingQueue<>();

    public ThreadPool(int size) {
        this.pool = new Thread[size];
        for (var i = 0; i < size; i++) {
            pool[i] = new Thread(() -> {
                try {
                    Future<?> future = tasks.take();
                    Object result = future.getTask().call();
                    future.done(result);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        for (Thread thread : pool) {
            thread.start();
        }
    }

    public Future<Void> submit(Runnable runnable) {
        Callable<Void> callable = () -> {
            runnable.run();
            return null;
        };
        return submit(callable);
    }

    public <T> Future<T> submit(Callable<T> callable) {
        var future = Future.of(callable);
        tasks.add(future);
        return future;
    }


    public void shutdown() {
        for (Thread thread : pool) {
            thread.interrupt();
        }
    }
}
