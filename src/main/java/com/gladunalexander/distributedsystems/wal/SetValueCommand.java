package com.gladunalexander.distributedsystems.wal;

import lombok.SneakyThrows;
import lombok.Value;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Value(staticConstructor = "of")
public class SetValueCommand implements WALCommand {

    private static final int HEADER_SIZE = 1 + 1 + 4;

    String key;
    String value;

    @Override
    public Type type() {
        return Type.SET;
    }

    @Override
    @SneakyThrows
    public ByteBuffer[] serialize() {
        var header = new byte[HEADER_SIZE];
        var headerBuffer = ByteBuffer.wrap(header);
        headerBuffer.put(0, (byte) type().id);
        headerBuffer.put(1, (byte) key.length());
        headerBuffer.putInt(2, value.length());

        return new ByteBuffer[]{
                headerBuffer,
                ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8)),
                ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8))
        };
    }

}
