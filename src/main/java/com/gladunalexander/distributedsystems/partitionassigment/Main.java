package com.gladunalexander.distributedsystems.partitionassigment;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        var partition = IntStream.range(0, 500)
                .mapToObj(Partition::new)
                .toList();

        var distributedLock = new DistributedLock();

        var node1 = new Node("node1", partition, distributedLock);
        var node2 = new Node("node2", partition, distributedLock);
        var node3 = new Node("node3", partition, distributedLock);
        var nodes = List.of(node1, node2, node3);

        var countDownLatch = new CountDownLatch(nodes.size());

        nodes.forEach(node ->
                CompletableFuture.runAsync(() -> {
                    node.process();
                    countDownLatch.countDown();
                }));

        countDownLatch.await();
    }
}
