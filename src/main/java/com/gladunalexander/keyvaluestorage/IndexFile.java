package com.gladunalexander.keyvaluestorage;

import lombok.SneakyThrows;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Iterator;

public class IndexFile implements Iterable<IndexFileEntry> {

    private static final String FILE_NAME = "kv.index";

    private final FileChannel channel;

    public IndexFile() throws IOException {
        var randomAccessFile = new RandomAccessFile(FILE_NAME, "rw");
        randomAccessFile.seek(randomAccessFile.length());
        this.channel = randomAccessFile.getChannel();
    }

    public void write(Key key, RecordMetadata recordMetadata) throws IOException {
        var indexFileEntry = IndexFileEntry.builder()
                                           .key(key)
                                           .recordMetadata(recordMetadata)
                                           .build();
        var serializeIndexFileEntry = indexFileEntry.serialize();
        channel.write(serializeIndexFileEntry);
    }

    public void close() throws Exception {
        channel.close();
    }

    @Override
    @SneakyThrows
    public Iterator<IndexFileEntry> iterator() {
        return new IndexFileIterator();
    }

    class IndexFileIterator implements Iterator<IndexFileEntry> {

        private final ByteBuffer buffer;

        public IndexFileIterator() throws IOException {
            buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }

        @Override
        public boolean hasNext() {
            return buffer.hasRemaining();
        }

        @Override
        public IndexFileEntry next() {
            if (hasNext()) {
                return IndexFileEntry.deserialize(buffer);
            }
            return null;
        }
    }

}
