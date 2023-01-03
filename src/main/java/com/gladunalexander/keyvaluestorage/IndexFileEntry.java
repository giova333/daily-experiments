package com.gladunalexander.keyvaluestorage;

import lombok.Builder;
import lombok.Value;

import java.nio.ByteBuffer;

@Value
@Builder
public class IndexFileEntry {

    /**
     * key size         - 1 bytes.
     * record offset    - 4 bytes.
     * record size      - 4 bytes.
     */
    private static final int INDEX_FILE_HEADER_SIZE = 9;
    private static final int KEY_SIZE_OFFSET = 0;
    private static final int RECORD_OFFSET = 1;
    private static final int RECORD_SIZE_OFFSET = 5;

    Key key;
    RecordMetadata recordMetadata;

    static IndexFileEntry deserialize(ByteBuffer buffer) {
        var keySize = buffer.get();
        var recordOffset = buffer.getInt();
        var recordSize = buffer.getInt();

        byte[] key = new byte[keySize];
        buffer.get(key);
        return IndexFileEntry.builder()
                             .key(Key.of(key))
                             .recordMetadata(RecordMetadata.from(recordOffset, recordSize))
                             .build();
    }

    ByteBuffer[] serialize() {
        byte[] header = new byte[INDEX_FILE_HEADER_SIZE];
        var h = ByteBuffer.wrap(header);
        h.put(KEY_SIZE_OFFSET, (byte) key.size());
        h.putInt(RECORD_OFFSET, recordMetadata.getOffset());
        h.putInt(RECORD_SIZE_OFFSET, recordMetadata.getSize());
        return new ByteBuffer[]{h, ByteBuffer.wrap(key.getValue())};
    }

}
