package com.gladunalexander.distributedsystems.replicatedlog;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class AppendEntriesRequest {
    List<LogEntry> entries;
    int commitIndex;
}
