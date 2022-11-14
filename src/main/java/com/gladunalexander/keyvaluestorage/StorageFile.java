package com.gladunalexander.keyvaluestorage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class StorageFile {

    private static final String FILE_NAME = "kv.data";

    private final FileChannel channel;

    public StorageFile() throws FileNotFoundException {
        this.channel = new RandomAccessFile(FILE_NAME, "rw").getChannel();
    }

    public ValueMetadata write(Key key, Value value) throws IOException {
        var header = Header.from(key, value);

        var byteBuffers = new ByteBuffer[]{header.toByteBuffer(), key.toByteBuffer(), value.toByteBuffer()};
        var prevPosition = channel.position();
        channel.write(byteBuffers);
        return ValueMetadata.builder()
                            .offset((int) (prevPosition + Header.HEADER_SIZE + key.size()))
                            .size(value.size())
                            .build();
    }

    public Value read(ValueMetadata metadata) throws IOException {

        var value = new byte[metadata.getSize()];
        var valueBuf = ByteBuffer.wrap(value);

        long currentPosition = metadata.getOffset();
        int bytesRead;
        do {
            bytesRead = channel.read(valueBuf, currentPosition);
            currentPosition += bytesRead;
        } while (bytesRead != -1 && valueBuf.hasRemaining());
        return Value.of(valueBuf.array());
    }

    public void close() throws Exception {
        channel.close();
    }
}
