package com.gladunalexander.distributedsystems.partitionassigment;

import lombok.experimental.FieldDefaults;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
public class DistributedLock {

    Set<Partition> lockedPartitions = ConcurrentHashMap.newKeySet();

    public boolean lock(Partition partition) {
        return lockedPartitions.add(partition);
    }

}
