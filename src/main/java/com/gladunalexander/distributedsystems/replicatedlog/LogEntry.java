package com.gladunalexander.distributedsystems.replicatedlog;

import lombok.Value;

@Value(staticConstructor = "of")
public class LogEntry {
    byte[] data;

    @Override
    public String toString() {
        return new String(data);
    }
}
