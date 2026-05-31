package com.gladunalexander.lsmkv.wal;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.zip.CRC32;

/**
 * Write-Ahead Log: an append-only file written <em>before</em> the memtable so that
 * acknowledged writes survive a crash.
 *
 * <p>Each record is {@code [crc32:4][keyLen:4][key][valLen:4][value]} where the CRC32 is
 * computed over the payload (everything after the checksum). {@code fsync} is called after
 * every record so the data reaches stable storage before the write is acknowledged.
 *
 * <p>On startup the log is replayed to rebuild the memtable; the first invalid or
 * truncated record (e.g. a torn write from a crash) terminates the replay and its tail is
 * discarded.
 */
public class Wal {

    private static final String FILE_NAME = "wal.log";

    private final RandomAccessFile file;
    private final FileChannel channel;

    public Wal(Path dataDir) {
        try {
            this.file = new RandomAccessFile(dataDir.resolve(FILE_NAME).toFile(), "rw");
            this.channel = file.getChannel();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to open WAL", e);
        }
    }

    /** Appends a put record and fsyncs it to disk before returning. */
    public synchronized void append(String key, String value) {
        byte[] k = key.getBytes(StandardCharsets.US_ASCII);
        byte[] v = value.getBytes(StandardCharsets.US_ASCII);

        ByteBuffer payload = ByteBuffer.allocate(4 + k.length + 4 + v.length);
        payload.putInt(k.length).put(k).putInt(v.length).put(v);
        byte[] payloadBytes = payload.array();

        CRC32 crc = new CRC32();
        crc.update(payloadBytes);

        ByteBuffer record = ByteBuffer.allocate(4 + payloadBytes.length);
        record.putInt((int) crc.getValue()).put(payloadBytes).flip();

        try {
            channel.position(channel.size());
            while (record.hasRemaining()) {
                channel.write(record);
            }
            channel.force(true); // fsync
        } catch (IOException e) {
            throw new UncheckedIOException("failed to append to WAL", e);
        }
    }

    /**
     * Replays the log into an ordered map, stopping at the first invalid/truncated record
     * and truncating the file so the corrupt tail is discarded.
     */
    public synchronized LinkedHashMap<String, String> replay() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        try {
            int size = (int) channel.size();
            ByteBuffer buf = ByteBuffer.allocate(size);
            channel.position(0);
            while (buf.hasRemaining() && channel.read(buf) > 0) {
                // keep reading
            }
            byte[] all = buf.array();
            ByteBuffer in = ByteBuffer.wrap(all);

            int validEnd = 0;
            while (in.remaining() >= 8) { // at least checksum + keyLen
                int recordStart = in.position();
                int checksum = in.getInt();
                int keyLen = in.getInt();
                if (keyLen < 0 || in.remaining() < keyLen + 4) {
                    break; // truncated
                }
                byte[] k = new byte[keyLen];
                in.get(k);
                int valLen = in.getInt();
                if (valLen < 0 || in.remaining() < valLen) {
                    break; // truncated
                }
                byte[] v = new byte[valLen];
                in.get(v);

                CRC32 crc = new CRC32();
                crc.update(all, recordStart + 4, in.position() - (recordStart + 4));
                if ((int) crc.getValue() != checksum) {
                    break; // corrupt record; discard this one and the rest
                }
                result.put(new String(k, StandardCharsets.US_ASCII),
                        new String(v, StandardCharsets.US_ASCII));
                validEnd = in.position();
            }

            if (validEnd < size) {
                channel.truncate(validEnd);
            }
            channel.position(channel.size());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to replay WAL", e);
        }
        return result;
    }

    /** Clears the log. Called after a flush, once its contents are durable in an SSTable. */
    public synchronized void reset() {
        try {
            channel.truncate(0);
            channel.position(0);
            channel.force(true);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to reset WAL", e);
        }
    }

    public synchronized void close() {
        try {
            channel.close();
            file.close();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to close WAL", e);
        }
    }
}
