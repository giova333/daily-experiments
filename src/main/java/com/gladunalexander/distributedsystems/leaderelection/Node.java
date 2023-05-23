package com.gladunalexander.distributedsystems.leaderelection;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Node {
    private String id;
    private List<Node> peers = new ArrayList<>();
    private volatile Node votedFor;
    private volatile int term = 1;
    private volatile NodeState nodeState = NodeState.FOLLOWER;
    private volatile ScheduledExecutorService leaderElectionTimer;
    private volatile ScheduledExecutorService heartBeatTimer;

    public Node(String id) {
        this.id = id;
    }

    public void addPeers(List<Node> peers) {
        this.peers.addAll(peers);
    }

    public void start() {
        scheduleLeaderElectionTimer();
    }

    public void scheduleLeaderElectionTimer() {
        if (leaderElectionTimer != null && !leaderElectionTimer.isShutdown()) {
            leaderElectionTimer.shutdownNow();
        }
        leaderElectionTimer = Executors.newSingleThreadScheduledExecutor();
        Runnable leaderElectionTask = () -> {
            log.info("Starting leader election...");
            nodeState = NodeState.CANDIDATE;
            requestVotesFromPeers();
        };
        var delay = getRandomNumberInRange(5, 15);
        leaderElectionTimer.schedule(leaderElectionTask, delay, TimeUnit.SECONDS);
    }

    @SneakyThrows
    private void requestVotesFromPeers() {
        term++;
        var voteRequest = new VoteRequest(term, id);
        var executorService = Executors.newFixedThreadPool(peers.size());
        var completionService = new ExecutorCompletionService<VoteResponse>(executorService);
        for (var peer : peers) {
            executorService.submit(() -> {
                completionService.submit(() -> peer.handle(voteRequest));
            });
        }
        var numOfVotes = 1; //self vote
        votedFor = this;
        for (int i = 0; i < peers.size(); i++) {
            var response = completionService.take();
            if (response.get().isGranted()) {
                numOfVotes++;
            }
            if (numOfVotes >= majority()) {
                becomeLeader();
                executorService.shutdownNow();
                return;
            }
        }
        scheduleLeaderElectionTimer();
        executorService.shutdownNow();
    }

    private synchronized VoteResponse handle(VoteRequest voteRequest) {
        if (voteRequest.getTerm() < term) {
            log.info("Denied to {} because term is lower than current {}:", voteRequest, term);
            return VoteResponse.denied();
        }
        if (voteRequest.getTerm() == term) {
            var voteResponse = Objects.equals(votedFor.id, voteRequest.getCandidateId())
                    ? VoteResponse.granted()
                    : VoteResponse.denied();
            log.info("VoteRequest has the same term: {}. Response: {}", voteRequest, voteResponse);
            return voteResponse;
        }
        term = voteRequest.getTerm();
        votedFor = nodeById(voteRequest.getCandidateId());
        log.info("Granted to: {}", voteRequest);
        becomeFollower();
        return VoteResponse.granted();
    }

    private void becomeLeader() {
        if (leaderElectionTimer != null) {
            leaderElectionTimer.shutdownNow();
        }
        nodeState = NodeState.LEADER;
        startHeartbeatTimer();
    }

    private void startHeartbeatTimer() {
        heartBeatTimer = Executors.newSingleThreadScheduledExecutor();
        heartBeatTimer.scheduleAtFixedRate(this::sendHeartbeats, 0, 2, TimeUnit.SECONDS);
    }

    @SneakyThrows
    private void sendHeartbeats() {
        log.info("Node: {} is sending heartbeats", id);
        var heartbeat = new HeartbeatRequest(term);
        var executorService = Executors.newFixedThreadPool(peers.size());
        var completionService = new ExecutorCompletionService<HeartbeatResponse>(executorService);
        for (var peer : peers) {
            completionService.submit(() -> peer.handle(heartbeat));
        }
        for (int i = 0; i < majority() - 1; i++) {
            var heartbeatResponse = completionService.take();
            if (heartbeatResponse.get().getTerm() > term) {
                becomeFollower();
            }
        }
        executorService.shutdownNow();
    }

    private synchronized HeartbeatResponse handle(HeartbeatRequest heartbeat) {
        if (heartbeat.getTerm() > term) {
            term = heartbeat.getTerm();
            votedFor = null;
            becomeFollower();
        }
        if (heartbeat.getTerm() == term) {
            scheduleLeaderElectionTimer();
        }
        return new HeartbeatResponse(term);
    }

    public void becomeFollower() {
        this.nodeState = NodeState.FOLLOWER;
        if (heartBeatTimer != null && !heartBeatTimer.isShutdown()) {
            heartBeatTimer.shutdownNow();
        }
        scheduleLeaderElectionTimer();
    }

    private int majority() {
        return peers.size() / 2 + 1;
    }

    private Node nodeById(String candidateId) {
        return peers.stream()
                .filter(node -> node.id.equals(candidateId))
                .findAny()
                .orElseThrow();
    }

    private int getRandomNumberInRange(int min, int max) {
        var random = ThreadLocalRandom.current();
        return random.nextInt(max - min) + min;
    }
}
