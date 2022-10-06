package com.gladunalexander.ratelimiter;

import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class RateLimiterService {

    private final Map<Long, RateLimiter> rateLimiterByUserId = new ConcurrentHashMap<>();
    private final Supplier<? extends RateLimiter> rateLimiterProvider;

    public boolean allow(long userId) {
        var rateLimiter = rateLimiterByUserId.computeIfAbsent(userId, __ -> rateLimiterProvider.get());
        return rateLimiter.allow();
    }
}
