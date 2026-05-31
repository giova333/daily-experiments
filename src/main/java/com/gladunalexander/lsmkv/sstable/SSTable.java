package com.gladunalexander.lsmkv.sstable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Sorted String Table with a block-based, binary on-disk format (week 7).
 *
 * <p>Layout: a sequence of data blocks (each a run of sorted entries), then a Bloom filter
 * over all keys, then a sparse index (the first key + offset of each block), then a fixed
 * footer pointing at the index and Bloom regions:
 *
 * <pre>
 *   [data block]... [bloom filter] [sparse index] [footer: idxOff,idxLen,bloomOff,bloomLen,magic]
 * </pre>
 *
 * <p>A point lookup checks the Bloom filter (skipping the file entirely on a definite miss),
 * then binary-searches the sparse index to find the one block that may hold the key, and
 * linearly scans just that block. Each entry is {@code [keyLen][key][flag][valLen][value]}
 * where {@code flag=1} marks a tombstone.
 */
public class SSTable {

    private static final int BLOCK_ENTRIES = 16;
    private static final int FOOTER_SIZE = 28; // idxOff(8)+idxLen(4)+bloomOff(8)+bloomLen(4)+magic(4)
    private static final int MAGIC = 0x4C534D4B; // "LSMK"

    private final Path file;

    public SSTable(Path file) {
        this.file = file;
    }

    private record IndexEntry(String firstKey, long offset, int length) {
    }

    /** Writes the given entries (which MUST be iterated in sorted key order) and fsyncs. */
    public static void write(Path file, Map<String, String> sortedEntries) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(sortedEntries.entrySet());
        BloomFilter bloom = BloomFilter.create(entries.size(), 0.01);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        List<IndexEntry> index = new ArrayList<>();

        try {
            for (int i = 0; i < entries.size(); i += BLOCK_ENTRIES) {
                int end = Math.min(i + BLOCK_ENTRIES, entries.size());
                long blockOffset = data.size();
                DataOutputStream block = new DataOutputStream(data);
                for (int j = i; j < end; j++) {
                    Map.Entry<String, String> e = entries.get(j);
                    writeEntry(block, e.getKey(), e.getValue());
                    bloom.add(e.getKey());
                }
                int blockLength = (int) (data.size() - blockOffset);
                index.add(new IndexEntry(entries.get(i).getKey(), blockOffset, blockLength));
            }

            byte[] dataBytes = data.toByteArray();
            byte[] bloomBytes = bloom.serialize();
            byte[] indexBytes = serializeIndex(index);

            long bloomOffset = dataBytes.length;
            long indexOffset = bloomOffset + bloomBytes.length;

            ByteBuffer footer = ByteBuffer.allocate(FOOTER_SIZE);
            footer.putLong(indexOffset).putInt(indexBytes.length)
                    .putLong(bloomOffset).putInt(bloomBytes.length).putInt(MAGIC);

            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
                raf.setLength(0);
                raf.write(dataBytes);
                raf.write(bloomBytes);
                raf.write(indexBytes);
                raf.write(footer.array());
                raf.getChannel().force(true); // fsync so the SSTable is durable
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write SSTable " + file, e);
        }
    }

    /**
     * Point lookup. Returns empty if the key is absent here; returns a tombstone {@link Slot}
     * if the key was deleted. Consults the Bloom filter first, then a single data block.
     */
    public Optional<Slot> lookup(String key) {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            Footer footer = readFooter(raf);

            BloomFilter bloom = BloomFilter.deserialize(readAt(raf, footer.bloomOffset, footer.bloomLength));
            if (!bloom.mightContain(key)) {
                return Optional.empty();
            }

            List<IndexEntry> index = deserializeIndex(readAt(raf, footer.indexOffset, footer.indexLength));
            IndexEntry block = blockFor(index, key);
            if (block == null) {
                return Optional.empty();
            }

            ByteBuffer in = ByteBuffer.wrap(readAt(raf, block.offset(), block.length()));
            while (in.hasRemaining()) {
                Entry entry = readEntry(in);
                if (entry.key().equals(key)) {
                    return Optional.of(entry.tombstone() ? Slot.deleted() : Slot.of(entry.value()));
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read SSTable " + file, e);
        }
    }

    /** All entries in this SSTable (key to value, {@code null} = tombstone), in key order. */
    public Map<String, String> entries() {
        Map<String, String> result = new LinkedHashMap<>();
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            Footer footer = readFooter(raf);
            ByteBuffer in = ByteBuffer.wrap(readAt(raf, 0, (int) footer.bloomOffset)); // data region
            while (in.hasRemaining()) {
                Entry entry = readEntry(in);
                result.put(entry.key(), entry.tombstone() ? null : entry.value());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read SSTable " + file, e);
        }
        return result;
    }

    /** Entries whose key is within {@code [start, end]} (inclusive). */
    public Map<String, String> entriesInRange(String start, String end) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : entries().entrySet()) {
            if (e.getKey().compareTo(start) >= 0 && e.getKey().compareTo(end) <= 0) {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    // --- encoding helpers ---

    private record Entry(String key, String value, boolean tombstone) {
    }

    private static void writeEntry(DataOutputStream out, String key, String value) throws IOException {
        byte[] k = key.getBytes(StandardCharsets.US_ASCII);
        boolean tombstone = value == null;
        byte[] v = tombstone ? new byte[0] : value.getBytes(StandardCharsets.US_ASCII);
        out.writeInt(k.length);
        out.write(k);
        out.writeByte(tombstone ? 1 : 0);
        out.writeInt(v.length);
        out.write(v);
    }

    private static Entry readEntry(ByteBuffer in) {
        int keyLen = in.getInt();
        byte[] k = new byte[keyLen];
        in.get(k);
        boolean tombstone = in.get() == 1;
        int valLen = in.getInt();
        byte[] v = new byte[valLen];
        in.get(v);
        return new Entry(new String(k, StandardCharsets.US_ASCII), tombstone ? null
                : new String(v, StandardCharsets.US_ASCII), tombstone);
    }

    private static byte[] serializeIndex(List<IndexEntry> index) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(out);
        try {
            dos.writeInt(index.size());
            for (IndexEntry e : index) {
                byte[] k = e.firstKey().getBytes(StandardCharsets.US_ASCII);
                dos.writeInt(k.length);
                dos.write(k);
                dos.writeLong(e.offset());
                dos.writeInt(e.length());
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("failed to serialize index", ex);
        }
        return out.toByteArray();
    }

    private static List<IndexEntry> deserializeIndex(byte[] bytes) {
        ByteBuffer in = ByteBuffer.wrap(bytes);
        int count = in.getInt();
        List<IndexEntry> index = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int keyLen = in.getInt();
            byte[] k = new byte[keyLen];
            in.get(k);
            long offset = in.getLong();
            int length = in.getInt();
            index.add(new IndexEntry(new String(k, StandardCharsets.US_ASCII), offset, length));
        }
        return index;
    }

    /** The last block whose first key is &le; the search key, or null if the key precedes all. */
    private static IndexEntry blockFor(List<IndexEntry> index, String key) {
        int lo = 0;
        int hi = index.size() - 1;
        IndexEntry candidate = null;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (index.get(mid).firstKey().compareTo(key) <= 0) {
                candidate = index.get(mid);
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return candidate;
    }

    private record Footer(long indexOffset, int indexLength, long bloomOffset, int bloomLength) {
    }

    private static Footer readFooter(RandomAccessFile raf) throws IOException {
        long len = raf.length();
        ByteBuffer in = ByteBuffer.wrap(readAt(raf, len - FOOTER_SIZE, FOOTER_SIZE));
        long indexOffset = in.getLong();
        int indexLength = in.getInt();
        long bloomOffset = in.getLong();
        int bloomLength = in.getInt();
        if (in.getInt() != MAGIC) {
            throw new IOException("bad SSTable footer magic");
        }
        return new Footer(indexOffset, indexLength, bloomOffset, bloomLength);
    }

    private static byte[] readAt(RandomAccessFile raf, long offset, int length) throws IOException {
        byte[] buf = new byte[length];
        raf.seek(offset);
        raf.readFully(buf);
        return buf;
    }
}
