package com.gladunalexander.distributedsystems.leaderelection;

import lombok.Value;

@Value
public class HeartbeatResponse {
    int term;
}
