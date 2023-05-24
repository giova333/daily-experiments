package com.gladunalexander.distributedsystems.replicatedlog;

import lombok.Value;

@Value
public class AppendEntriesResponse {
    boolean success;
    int lastIndex;
}
