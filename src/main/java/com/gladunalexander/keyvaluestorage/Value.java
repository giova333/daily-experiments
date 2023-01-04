package com.gladunalexander.keyvaluestorage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@lombok.Value(staticConstructor = "of")
public class Value {
    byte[] value;

    public static Value empty() {
        return new Value(new byte[0]);
    }

    public static Value of(String key) {
        return new Value(key.getBytes(StandardCharsets.UTF_8));
    }

    ByteBuffer toByteBuffer() {
        return ByteBuffer.wrap(value);
    }

    int size() {
        return value.length;
    }

    boolean isEmpty() {
        return value.length == 0;
    }

    @Override
    public String toString() {
        return new String(value);
    }
}
