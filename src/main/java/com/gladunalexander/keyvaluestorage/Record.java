package com.gladunalexander.keyvaluestorage;

import lombok.Builder;

import java.nio.ByteBuffer;

@lombok.Value
@Builder
public class Record {
    Header header;
    Key key;
    Value value;

    public static Record from(byte[] array) {
        var byteBuffer = ByteBuffer.wrap(array);
        var header = Header.from(byteBuffer.getInt(), byteBuffer.get(), byteBuffer.getInt());
        var key = new byte[header.getKeySize()];
        var value = new byte[header.getValueSize()];
        byteBuffer.get(key);
        byteBuffer.get(value);
        return Record.builder()
                     .header(header)
                     .key(Key.of(key))
                     .value(Value.of(value))
                     .build();
    }
}
