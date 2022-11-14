package com.gladunalexander.keyvaluestorage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@lombok.Value(staticConstructor = "of")
public class Value {
    byte[] value;

    ByteBuffer toByteBuffer() {
        return ByteBuffer.wrap(value);
    }

    public static Value of(String key) {
        return new Value(key.getBytes(StandardCharsets.UTF_8));
    }

    int size() {
        return value.length;
    }
}
