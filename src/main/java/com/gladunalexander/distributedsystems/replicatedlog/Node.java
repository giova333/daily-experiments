package com.gladunalexander.distributedsystems.replicatedlog;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;

@Slf4j
public class Node {
    private final String id;
    private final NodeRole nodeRole;
    private final Log appendLog = new Log();
    private final List<Node> peers = new ArrayList<>();
    private final StateMachine stateMachine = new StateMachine();
    private final Executor replicationExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private int commitIndex = -1;

    public Node(String id, NodeRole nodeRole) {
        this.id = id;
        this.nodeRole = nodeRole;
    }

    public void addPeers(List<Node> peers) {
        this.peers.addAll(peers);
    }

    @SneakyThrows
    public void append(byte[] data) {
        if (!isLeader()) {
            throw new IllegalStateException("Only leader can append data to the log");
        }
        var logEntry = LogEntry.of(data);
        appendLog.append(logEntry);
        replicateToMajority(logEntry);
    }

    private void replicateToMajority(LogEntry logEntry) throws InterruptedException, ExecutionException {
        log.info("Replicating entry: {}", logEntry);
        var completionService = new ExecutorCompletionService<AppendEntriesResponse>(replicationExecutor);
        for (var peer : peers) {
            completionService.submit(() -> replicate(logEntry, peer));
        }
        var replicatedCount = 1;
        for (int i = 0; i < peers.size(); i++) {
            var responseFuture = completionService.take();
            if (responseFuture.get().isSuccess()) {
                replicatedCount++;
            }
            if (replicatedCount >= majority()) {
                commitIndex++;
                applyToStateMachine(commitIndex);
                return;
            }
        }
    }

    private AppendEntriesResponse replicate(LogEntry logEntry,
                                            Node peer) {
        var appendEntriesRequest = AppendEntriesRequest.builder()
                .entries(List.of(logEntry))
                .commitIndex(commitIndex)
                .build();

        var response = peer.handle(appendEntriesRequest);
        if (response.isSuccess()) {
            return response;
        }
        var lastIndex = response.getLastIndex();
        appendEntriesRequest = appendEntriesRequest.toBuilder()
                .entries(appendLog.from(lastIndex + 1))
                .build();
        return peer.handle(appendEntriesRequest);
    }

    private void applyToStateMachine(int commitIndex) {
        var logEntry = appendLog.get(commitIndex);
        log.info("Node {} applying entry to state machine: {}", id, logEntry);
        stateMachine.accept(logEntry);
    }

    private AppendEntriesResponse handle(AppendEntriesRequest appendEntriesRequest) {
        var entries = appendEntriesRequest.getEntries();
        if (needsMoreEntriesToCatchUp(appendEntriesRequest, entries)) {
            return new AppendEntriesResponse(false, appendLog.lastIndex());
        }
        entries.forEach(appendLog::append);
        for (int i = commitIndex + 1; i <= appendEntriesRequest.getCommitIndex(); i++) {
            applyToStateMachine(i);
        }
        commitIndex = appendEntriesRequest.getCommitIndex();
        return new AppendEntriesResponse(true, appendLog.lastIndex());
    }

    private boolean needsMoreEntriesToCatchUp(AppendEntriesRequest appendEntriesRequest,
                                              List<LogEntry> entries) {
        return appendLog.size() + entries.size() != appendEntriesRequest.getCommitIndex() + 2;
    }

    private boolean isLeader() {
        return nodeRole == NodeRole.LEADER;
    }

    private int majority() {
        return peers.size() / 2 + 1;
    }

}
