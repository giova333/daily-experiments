package com.gladunalexander.consistenthashing;

import org.apache.commons.codec.digest.MurmurHash3;

import java.nio.charset.StandardCharsets;

public class MurmurHashFunction implements HashFunction {
    @Override
    public long hash(String key) {
        return MurmurHash3.hash32x86(key.getBytes(StandardCharsets.UTF_8));
    }
}
