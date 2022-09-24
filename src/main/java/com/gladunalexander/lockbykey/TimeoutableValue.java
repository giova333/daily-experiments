package com.gladunalexander.lockbykey;

import lombok.Value;

@Value
public class TimeoutableValue<T> {
    T value;
    long expiresAt;
}
