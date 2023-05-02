package com.gladunalexander.distributedsystems.vectorclock;

import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
@ToString
public class VectorClock implements Comparable<VectorClock> {

    private final Map<String, Long> counters;

    public static VectorClock create() {
        return new VectorClock(Map.of());
    }

    public VectorClock increment(String key) {
        var newCounters = new HashMap<>(counters);
        newCounters.merge(key, 1L, Long::sum);
        return new VectorClock(newCounters);
    }

    public VectorClock merge(VectorClock other) {
        var newCounters = new HashMap<>(counters);
        for (var entry : other.counters.entrySet()) {
            newCounters.merge(entry.getKey(), entry.getValue(), Math::max);
        }
        return new VectorClock(newCounters);
    }

    @Override
    public int compareTo(VectorClock other) {
        boolean isGreater = false;
        boolean isLess = false;

        var allKeys = Stream.concat(
                                    counters.keySet().stream(),
                                    other.counters.keySet().stream())
                            .collect(Collectors.toSet());

        for (var key : allKeys) {
            var currentValue = counters.getOrDefault(key, 0L);
            var otherValue = other.counters.getOrDefault(key, 0L);
            if (currentValue > otherValue) {
                isGreater = true;
            } else if (currentValue < otherValue) {
                isLess = true;
            }
        }
        return (isGreater && isLess) ? 0
                : isGreater ? 1
                : isLess ? -1
                : 0;
    }
}

