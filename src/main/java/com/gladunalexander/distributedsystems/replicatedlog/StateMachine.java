package com.gladunalexander.distributedsystems.replicatedlog;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
public class StateMachine implements Consumer<LogEntry> {

    @Override
    public void accept(LogEntry entry) {
    }
}
