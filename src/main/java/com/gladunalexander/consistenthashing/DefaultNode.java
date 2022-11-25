package com.gladunalexander.consistenthashing;

import lombok.Value;

@Value
public class DefaultNode implements Node {
    String ip;
    int port;

    @Override
    public String key() {
        return ip + ":" + port;
    }
}
