package com.gladunalexander.distributedsystems.replicatedlog;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class ReplicatedLogTest {

    public static void main(String[] args) throws InterruptedException {
        var leader = new Node("1", NodeRole.LEADER);
        var follower1 = new Node("2", NodeRole.FOLLOWER);
        var follower2 = new Node("3", NodeRole.FOLLOWER);
        var follower3 = new Node("4", NodeRole.FOLLOWER);

        leader.addPeers(List.of(follower1, follower2));

        for (int i = 0; i < 1000; i++) {
            if (i == 5) {
                leader.addPeers(List.of(follower3));
            }
            var message = "Message: " + i;
            leader.append(message.getBytes(StandardCharsets.UTF_8));
            Thread.sleep(3000);
        }
    }
}
