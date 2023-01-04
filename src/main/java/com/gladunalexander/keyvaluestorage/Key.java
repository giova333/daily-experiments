package com.gladunalexander.keyvaluestorage;

import lombok.Value;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Value(staticConstructor = "of")
public class Key {
    byte[] value;

    ByteBuffer toByteBuffer() {
        return ByteBuffer.wrap(value);
    }

    public static Key of(String key) {
        return new Key(key.getBytes(StandardCharsets.UTF_8));
    }

    int size() {
        return value.length;
    }

    @Override
    public String toString() {
        return new String(value);
    }
}
