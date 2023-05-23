package com.gladunalexander.distributedsystems.leaderelection;

import java.util.List;

public class LeaderElectionTest {

    public static void main(String[] args) throws InterruptedException {
        var node1 = new Node("1");
        var node2 = new Node("2");
        var node3 = new Node("3");

        node1.addPeers(List.of(node2, node3));
        node2.addPeers(List.of(node1, node3));
        node3.addPeers(List.of(node1, node2));

        node1.start();
        node2.start();
        node3.start();

        Thread.sleep(1000000);
    }
}
