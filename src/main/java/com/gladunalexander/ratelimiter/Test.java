package com.gladunalexander.ratelimiter;

import java.util.function.Supplier;

public class Test {

    public static void main(String[] args) throws InterruptedException {
        var rateLimiterConfiguration = RateLimiterConfiguration.builder()
                                            .requests(5)
                                            .seconds(60)
                                            .build();

        Supplier<TokeBucketRateLimiter> rateLimiterProvider = () -> new TokeBucketRateLimiter(rateLimiterConfiguration);
        var rateLimiterService = new RateLimiterService(rateLimiterProvider);
        System.out.println(rateLimiterService.allow(1));
        System.out.println(rateLimiterService.allow(1));
        System.out.println(rateLimiterService.allow(1));
        System.out.println(rateLimiterService.allow(1));
        System.out.println(rateLimiterService.allow(1));
        System.out.println(rateLimiterService.allow(1));
        System.out.println(rateLimiterService.allow(2));
    }
}
