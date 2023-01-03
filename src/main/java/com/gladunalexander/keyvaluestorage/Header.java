package com.gladunalexander.keyvaluestorage;

import java.nio.ByteBuffer;

@lombok.Value
public class Header {
    /**
     * checksum         - 4 bytes.
     * key size         - 1 bytes.
     * value size       - 4 bytes.
     */
    static final int HEADER_SIZE = 9;
    static final int CHECKSUM_OFFSET = 0;
    static final int KEY_OFFSET = 4;
    static final int VALUE_OFFSET = 5;

    int checksum;
    byte keySize;
    int valueSize;

    static Header from(int checksum, Key key, Value value) {
        return new Header(checksum, (byte) key.size(), value.size());
    }

    static Header from(int checksum, byte keySize, int valueSize) {
        return new Header(checksum, keySize, valueSize);
    }

    ByteBuffer toByteBuffer() {
        var header = new byte[HEADER_SIZE];
        var headerBuffer = ByteBuffer.wrap(header);
        headerBuffer.putInt(CHECKSUM_OFFSET, checksum);
        headerBuffer.put(KEY_OFFSET, keySize);
        headerBuffer.putInt(VALUE_OFFSET, valueSize);
        return headerBuffer;
    }

}
