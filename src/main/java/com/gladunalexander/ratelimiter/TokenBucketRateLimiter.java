package com.gladunalexander.ratelimiter;

public class TokenBucketRateLimiter implements RateLimiter {

    private int availableTokens;
    private int maxTokens;
    private long lastRefillTsl;
    private double refillRate;

    public TokenBucketRateLimiter(RateLimiterConfiguration rateLimiterConfiguration) {
        availableTokens = rateLimiterConfiguration.getRequests();
        maxTokens = rateLimiterConfiguration.getRequests();
        lastRefillTsl = System.currentTimeMillis();
        refillRate = (double) rateLimiterConfiguration.getRequests() / rateLimiterConfiguration.getSeconds();

    }

    @Override
    public synchronized boolean allow() {
        refill();
        if (availableTokens > 0) {
            availableTokens--;
            return true;
        }
        return false;
    }

    private void refill() {
        var currentTimeTs = System.currentTimeMillis();
        var tokensToRefill = ((double) currentTimeTs - lastRefillTsl) / 1000 * refillRate;
        if ((int) tokensToRefill > 0) {
            availableTokens = (int) Math.min(maxTokens, availableTokens + tokensToRefill);
            lastRefillTsl = currentTimeTs;
        }
    }
}
