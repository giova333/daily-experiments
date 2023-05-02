package com.gladunalexander.distributedsystems.wal;

import lombok.RequiredArgsConstructor;

import java.nio.ByteBuffer;
import java.util.Arrays;

public interface WALCommand {

    ByteBuffer[] serialize();

    Type type();

    @RequiredArgsConstructor
    enum Type {
        SET(0),
        DEL(1);

        final int id;

        public static Type from(int id) {
            return Arrays.stream(values())
                    .filter(type -> type.id == id)
                    .findAny()
                    .orElseThrow();
        }
    }
}
