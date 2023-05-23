package com.gladunalexander.distributedsystems.leaderelection;

import lombok.Value;

@Value
public class VoteResponse {
    boolean granted;

    public static VoteResponse granted() {
        return new VoteResponse(true);
    }

    public static VoteResponse denied() {
        return new VoteResponse(false);
    }
}
