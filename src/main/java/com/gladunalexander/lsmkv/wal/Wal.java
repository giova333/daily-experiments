package com.gladunalexander.lsmkv.wal;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.zip.CRC32;

/**
 * Write-Ahead Log: an append-only file written <em>before</em> the memtable so acknowledged
 * writes survive a crash.
 *
 * <p>Each record is {@code [crc32:4][op:1][keyLen:4][key][valLen:4][value]} (op {@code 0} =
 * put, {@code 1} = delete); the CRC32 covers the payload, and {@code fsync} runs after every
 * record.
 *
 * <p>To support concurrent flushing (week 8) the log is segmented. When the active memtable
 * is rotated for flushing, {@link #rotate()} seals the current segment as {@code wal.old.log}
 * and starts a fresh {@code wal.log} for the new active memtable. Once the rotated memtable
 * is durable in an SSTable, {@link #discardOld()} removes the sealed segment. Recovery
 * replays the sealed segment (if any) then the current one, stopping at the first corrupt or
 * truncated record.
 */
public class Wal {

    private static final String CURRENT = "wal.log";
    private static final String OLD = "wal.old.log";
    private static final byte OP_PUT = 0;
    private static final byte OP_DELETE = 1;

    private final Path dir;
    private final Path currentPath;
    private final Path oldPath;
    private RandomAccessFile file;
    private FileChannel channel;

    public Wal(Path dataDir) {
        this.dir = dataDir;
        this.currentPath = dataDir.resolve(CURRENT);
        this.oldPath = dataDir.resolve(OLD);
        open();
    }

    private void open() {
        try {
            this.file = new RandomAccessFile(currentPath.toFile(), "rw");
            this.channel = file.getChannel();
            channel.position(channel.size());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to open WAL", e);
        }
    }

    public synchronized void appendPut(String key, String value) {
        append(OP_PUT, key, value);
    }

    public synchronized void appendDelete(String key) {
        append(OP_DELETE, key, "");
    }

    private void append(byte op, String key, String value) {
        byte[] k = key.getBytes(StandardCharsets.US_ASCII);
        byte[] v = value.getBytes(StandardCharsets.US_ASCII);

        ByteBuffer payload = ByteBuffer.allocate(1 + 4 + k.length + 4 + v.length);
        payload.put(op).putInt(k.length).put(k).putInt(v.length).put(v);
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

    /** Seals the current segment as the old segment and starts a fresh current segment. */
    public synchronized void rotate() {
        try {
            channel.force(true);
            channel.close();
            file.close();
            Files.move(currentPath, oldPath, StandardCopyOption.ATOMIC_MOVE);
            open(); // fresh, empty current segment
        } catch (IOException e) {
            throw new UncheckedIOException("failed to rotate WAL", e);
        }
    }

    /** Deletes the sealed segment once its memtable is durable in an SSTable. */
    public synchronized void discardOld() {
        try {
            Files.deleteIfExists(oldPath);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to discard old WAL segment", e);
        }
    }

    /**
     * Replays the sealed segment (if any) then the current segment into an ordered map (key
     * to value, {@code null} = tombstone), then consolidates everything into a single fresh
     * current segment so recovery is idempotent.
     */
    public synchronized LinkedHashMap<String, String> replayAll() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        try {
            if (Files.exists(oldPath)) {
                parse(Files.readAllBytes(oldPath), result);
            }
            parse(readChannelFully(), result);
            rewriteCurrent(result); // consolidate into one clean segment, drop the old one
        } catch (IOException e) {
            throw new UncheckedIOException("failed to replay WAL", e);
        }
        return result;
    }

    private byte[] readChannelFully() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) channel.size());
        channel.position(0);
        while (buf.hasRemaining() && channel.read(buf) > 0) {
            // keep reading
        }
        return buf.array();
    }

    /** Parses records into {@code out}, stopping at the first corrupt/truncated record. */
    private static void parse(byte[] all, LinkedHashMap<String, String> out) {
        ByteBuffer in = ByteBuffer.wrap(all);
        while (in.remaining() >= 9) { // checksum + op + keyLen
            int recordStart = in.position();
            int checksum = in.getInt();
            byte op = in.get();
            int keyLen = in.getInt();
            if (keyLen < 0 || in.remaining() < keyLen + 4) {
                break;
            }
            byte[] k = new byte[keyLen];
            in.get(k);
            int valLen = in.getInt();
            if (valLen < 0 || in.remaining() < valLen) {
                break;
            }
            byte[] v = new byte[valLen];
            in.get(v);

            CRC32 crc = new CRC32();
            crc.update(all, recordStart + 4, in.position() - (recordStart + 4));
            if ((int) crc.getValue() != checksum) {
                break;
            }
            out.put(new String(k, StandardCharsets.US_ASCII),
                    op == OP_DELETE ? null : new String(v, StandardCharsets.US_ASCII));
        }
    }

    private void rewriteCurrent(LinkedHashMap<String, String> entries) throws IOException {
        channel.close();
        file.close();
        Files.deleteIfExists(oldPath);
        Files.deleteIfExists(currentPath);
        open();
        for (var e : entries.entrySet()) {
            append(e.getValue() == null ? OP_DELETE : OP_PUT, e.getKey(),
                    e.getValue() == null ? "" : e.getValue());
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
