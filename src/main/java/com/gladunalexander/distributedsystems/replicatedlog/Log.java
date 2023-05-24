package com.gladunalexander.distributedsystems.replicatedlog;

import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Value
public class Log {
    List<LogEntry> entries = new ArrayList<>();

    public void append(LogEntry entry) {
        entries.add(entry);
    }

    public LogEntry get(int index) {
        return entries.get(index);
    }

    public int size() {
        return entries.size();
    }

    public int lastIndex() {
        return size() - 1;
    }

    public List<LogEntry> from(int lastIndex) {
        return from(lastIndex, entries.size());
    }

    public List<LogEntry> from(int start, int end) {
        return entries.subList(start, end);
    }
}
