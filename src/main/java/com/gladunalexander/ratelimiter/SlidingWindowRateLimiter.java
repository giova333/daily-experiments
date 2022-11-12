package com.gladunalexander.ratelimiter;

import java.util.LinkedList;
import java.util.Queue;

public class SlidingWindowRateLimiter implements RateLimiter {

    private final Queue<Long> requestTimestamps = new LinkedList<>();
    private final RateLimiterConfiguration configuration;

    public SlidingWindowRateLimiter(RateLimiterConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public synchronized boolean allow() {
        var currentTimeMillis = System.currentTimeMillis();
        updateQueue(currentTimeMillis);
        if (requestTimestamps.size() >= configuration.getRequests()) {
            return false;
        }
        requestTimestamps.offer(currentTimeMillis);
        return true;
    }

    private void updateQueue(long currentTimeMillis) {
        var timestamp = currentTimeMillis - configuration.getSeconds() * 1000L;
        while (requestTimestamps.peek() != null && requestTimestamps.peek() < timestamp) {
            requestTimestamps.poll();
        }
    }
}
