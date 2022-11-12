package com.gladunalexander.ratelimiter;

import java.util.function.Supplier;

public class Test {

    public static void main(String[] args) throws Exception {
        var rateLimiterConfiguration = RateLimiterConfiguration.builder()
                                            .requests(5)
                                            .seconds(20)
                                            .build();

        Supplier<RateLimiter> rateLimiterProvider = () -> new TokenBucketRateLimiter(rateLimiterConfiguration);
        var rateLimiterService = new RateLimiterService(rateLimiterProvider);

        for (var i = 0; i < 1000; i++) {
            System.out.println(rateLimiterService.allow(1));
            Thread.sleep(1000);
        }
    }
}
