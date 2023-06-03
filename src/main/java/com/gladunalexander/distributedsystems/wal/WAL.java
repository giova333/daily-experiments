package com.gladunalexander.distributedsystems.wal;

import lombok.SneakyThrows;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class WAL {

    private final FileChannel fileChannel;

    @SneakyThrows
    private WAL(File file) {
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        randomAccessFile.seek(randomAccessFile.length());
        this.fileChannel = randomAccessFile.getChannel();
    }

    public static WAL openWal(KVStore.Configuration configuration) {
        return new WAL(new File(configuration.getWalFileName()));
    }

    @SneakyThrows
    public void writeEntry(WALCommand command) {
        var serialized = command.serialize();
        fileChannel.write(serialized);
        fileChannel.force(false);
    }

    @SneakyThrows
    public List<WALCommand> readWALEntries() {
        var entries = new ArrayList<WALCommand>();

        var buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());

        while (buffer.hasRemaining()) {
            byte typeByte = buffer.get();
            var type = WALCommand.Type.from(typeByte);

            switch (type) {
                case SET:
                    entries.add(readSetValueCommand(buffer));
                    break;
                case DEL:
                    entries.add(readDelValueCommand(buffer));
                    break;
                default:
                    throw new RuntimeException("Invalid command type found in the WAL.");
            }
        }
        return entries;
    }

    private WALCommand readDelValueCommand(ByteBuffer buffer) {
        var keyLength = buffer.get();
        var keyBytes = new byte[keyLength];
        buffer.get(keyBytes);
        var key = new String(keyBytes, StandardCharsets.UTF_8);

        return DelValueCommand.of(key);
    }

    private WALCommand readSetValueCommand(ByteBuffer buffer) {
        var keyLength = buffer.get();
        var keyBytes = new byte[keyLength];

        var valueLength = buffer.getInt();
        var valueBytes = new byte[valueLength];

        buffer.get(keyBytes);
        var key = new String(keyBytes, StandardCharsets.UTF_8);

        buffer.get(valueBytes);
        var value = new String(valueBytes, StandardCharsets.UTF_8);

        return SetValueCommand.of(key, value);
    }
}
