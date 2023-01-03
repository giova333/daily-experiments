package com.gladunalexander.keyvaluestorage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.CRC32;

public class StorageFile {

    private static final String FILE_NAME = "kv.data";

    private final FileChannel channel;

    public StorageFile() throws FileNotFoundException {
        this.channel = new RandomAccessFile(FILE_NAME, "rw").getChannel();
    }

    public RecordMetadata write(Key key, Value value) throws IOException {
        var checkSum = calculateCheckSum(value);
        var header = Header.from(checkSum, key, value);

        var byteBuffers = new ByteBuffer[]{header.toByteBuffer(), key.toByteBuffer(), value.toByteBuffer()};
        var prevPosition = channel.position();
        channel.write(byteBuffers);
        return RecordMetadata.builder()
                             .offset((int) prevPosition)
                             .size(Header.HEADER_SIZE + key.size() + value.size())
                             .build();
    }

    public Record read(RecordMetadata recordMetadata) throws IOException {

        var value = new byte[recordMetadata.getSize()];
        var valueBuf = ByteBuffer.wrap(value);

        long currentPosition = recordMetadata.getOffset();
        int bytesRead;
        do {
            bytesRead = channel.read(valueBuf, currentPosition);
            currentPosition += bytesRead;
        } while (bytesRead != -1 && valueBuf.hasRemaining());
        var record = Record.from(valueBuf.array());
        verifyCheckSum(record);
        return record;
    }

    public void close() throws Exception {
        channel.close();
    }

    private void verifyCheckSum(Record record) {
        if (calculateCheckSum(record.getValue()) != record.getHeader().getChecksum()) {
            throw new IllegalStateException("Invalid record check sum");
        }
    }

    private int calculateCheckSum(Value value) {
        var crc32 = new CRC32();
        crc32.update(value.getValue());
        var checksum = crc32.getValue();
        return Utils.toSignedIntFromLong(checksum);
    }
}
