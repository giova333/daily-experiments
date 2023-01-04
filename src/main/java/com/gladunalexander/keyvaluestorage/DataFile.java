package com.gladunalexander.keyvaluestorage;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.CRC32;

public class DataFile {

    private static final String FILE_NAME = "kv.data";

    private final FileChannel channel;

    public DataFile() throws IOException {
        var randomAccessFile = new RandomAccessFile(FILE_NAME, "rw");
        randomAccessFile.seek(randomAccessFile.length());
        this.channel = randomAccessFile.getChannel();
    }

    public RecordMetadata write(Key key, Value value, long ttl) throws IOException {
        var checkSum = calculateCheckSum(value);
        var header = Header.from(checkSum, ttl, key, value);

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
        var record = Record.from(valueBuf.position(0));
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
