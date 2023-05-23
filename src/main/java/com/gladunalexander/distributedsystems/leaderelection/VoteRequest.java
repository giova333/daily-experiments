package com.gladunalexander.distributedsystems.leaderelection;

import lombok.Value;

@Value
public class VoteRequest {
    int term;
    String candidateId;
}
