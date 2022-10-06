package com.gladunalexander.ratelimiter;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class RateLimiterConfiguration {
    @NonNull
    int requests;
    @NonNull
    int seconds;
}
