package com.gladunalexander.threadpool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

public class ThreadPool {

    private final Thread[] pool;
    private final BlockingQueue<Future<?>> tasks = new LinkedBlockingQueue<>();
    private boolean acceptNewTasks = true;

    public ThreadPool(int size) {
        this.pool = new Thread[size];
        for (var i = 0; i < size; i++) {
            pool[i] = new Thread(() -> {
                var active = true;
                while (active) {
                    Future<?> future = null;
                    try {
                        future = tasks.take();
                        Object result = future.getTask().call();
                        future.done(result);
                    } catch (InterruptedException e) {
                        active = false;
                    } catch (Exception e) {
                        if (future != null) {
                            future.completeExceptionally(e);
                        }
                    }
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
        if (acceptNewTasks) {
            tasks.add(future);
        }
        return future;
    }

    public void shutdown() {
        acceptNewTasks = false;
        while (!tasks.isEmpty()) {
            Thread.onSpinWait();
        }
        shutdownNow();
    }


    public void shutdownNow() {
        for (Thread thread : pool) {
            thread.interrupt();
        }
    }
}
