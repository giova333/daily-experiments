package com.gladunalexander.distributedsystems.partitionassigment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public record Node(String id,
                   List<Partition> partitions,
                   DistributedLock distributedLock) {

    public void process() {
        var partitionsOwnedByCurrentNode = getPartitionsOwnedByCurrentNode();
        System.out.println("Node " + id + " processing partitions: " + partitionsOwnedByCurrentNode);
    }

    private List<Partition> getPartitionsOwnedByCurrentNode() {
        var partitionsOwnedByCurrentNode = new ArrayList<Partition>();
        var start = ThreadLocalRandom.current().nextInt(0, partitions.size());

        for (int i = start; i < start + partitions.size(); i++) {
            var partitionIndex = partitions.get(i % partitions.size());
            if (distributedLock.lock(partitionIndex)) {
                partitionsOwnedByCurrentNode.add(partitionIndex);
            }
        }
        return partitionsOwnedByCurrentNode;
    }

}
