package com.gladunalexander.keyvaluestorage;

import lombok.Builder;

import java.nio.ByteBuffer;

@lombok.Value
@Builder
public class Record {
    Header header;
    Key key;
    Value value;

    public static Record from(ByteBuffer byteBuffer) {
        var header = Header.from(byteBuffer.getInt(), byteBuffer.getLong(), byteBuffer.get(), byteBuffer.getInt());
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

    public boolean isTombstone() {
        return value.isEmpty();
    }

    public boolean isExpired() {
        var ttl = header.getTtl();
        return ttl != -1 && ttl <= System.currentTimeMillis();
    }
}
