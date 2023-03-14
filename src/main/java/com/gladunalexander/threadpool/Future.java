package com.gladunalexander.threadpool;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;

public class Future<T> {

    private volatile T result;
    private volatile Exception exception;
    private volatile Status status = Status.CREATED;
    private final Callable<T> task;

    private Future(Callable<T> task) {
        this.task = task;
    }

    public static <T> Future<T> of(Callable<T> task) {
        return new Future<>(task);
    }

    public Optional<T> get() {
        return Optional.ofNullable(result);
    }

    public Optional<T> getAwaiting() {
        return getAwaiting(Duration.ofDays(Integer.MAX_VALUE));
    }

    public Optional<T> getAwaiting(Duration duration) {
        var waitUntil = duration.toMillis() + System.currentTimeMillis();
        while (waitUntil > System.currentTimeMillis() && status != Status.CANCELLED) {
            if (status == Status.DONE) return Optional.ofNullable(result);
            if (status == Status.FAILED) throw new RuntimeException(exception);
        }
        return Optional.ofNullable(result);
    }

    public void cancel() {
        status = Status.CANCELLED;
    }

    @SuppressWarnings("unchecked")
    void done(Object result) {
        this.result = (T) result;
        this.status = Status.DONE;
    }

    Callable<T> getTask() {
        return task;
    }

    public void completeExceptionally(Exception e) {
        this.exception = e;
        this.status = Status.FAILED;
    }

    enum Status {
        CREATED, DONE, CANCELLED, FAILED

    }
}
