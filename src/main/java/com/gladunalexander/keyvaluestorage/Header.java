package com.gladunalexander.keyvaluestorage;

import java.nio.ByteBuffer;

@lombok.Value
public class Header {
    /**
     * key size         - 1 bytes.
     * value size       - 4 bytes.
     */
    public static final int HEADER_SIZE = 5;
    private static final int KEY_OFFSET = 0;
    private static final int VALUE_OFFSET = 1;

    byte keySize;
    int valueSize;

    static Header from(Key key, Value value) {
        return new Header((byte) key.size(), value.size());
    }

    ByteBuffer toByteBuffer() {
        var header = new byte[HEADER_SIZE];
        var headerBuffer = ByteBuffer.wrap(header);
        headerBuffer.put(KEY_OFFSET, keySize);
        headerBuffer.putInt(VALUE_OFFSET, valueSize);
        return headerBuffer;
    }

}
