package com.gladunalexander.lsmkv.sstable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * A space-efficient, probabilistic set membership filter stored per SSTable.
 *
 * <p>It answers one question: is this key <em>definitely not</em> in the SSTable? There are
 * no false negatives, so a "no" lets the read path skip the SSTable entirely without
 * touching its data blocks; a "maybe" may be a false positive and is confirmed by an actual
 * lookup.
 *
 * <p>Bit positions are derived by double hashing two independent hashes (FNV-1a and djb2).
 */
public final class BloomFilter {

    private final int numBits;
    private final int numHashes;
    private final BitSet bits;

    private BloomFilter(int numBits, int numHashes, BitSet bits) {
        this.numBits = numBits;
        this.numHashes = numHashes;
        this.bits = bits;
    }

    /** Sizes a filter for the expected number of entries and target false-positive rate. */
    public static BloomFilter create(int expectedEntries, double falsePositiveRate) {
        int n = Math.max(1, expectedEntries);
        int m = Math.max(8, (int) Math.ceil(-(n * Math.log(falsePositiveRate))
                / (Math.log(2) * Math.log(2))));
        int k = Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
        return new BloomFilter(m, k, new BitSet(m));
    }

    public void add(String key) {
        for (int idx : indices(key)) {
            bits.set(idx);
        }
    }

    public boolean mightContain(String key) {
        for (int idx : indices(key)) {
            if (!bits.get(idx)) {
                return false;
            }
        }
        return true;
    }

    private int[] indices(String key) {
        byte[] bytes = key.getBytes(StandardCharsets.US_ASCII);
        int h1 = fnv1a(bytes);
        int h2 = djb2(bytes);
        int[] result = new int[numHashes];
        for (int i = 0; i < numHashes; i++) {
            long combined = (h1 & 0xffffffffL) + (long) i * (h2 & 0xffffffffL);
            result[i] = (int) Long.remainderUnsigned(combined, numBits);
        }
        return result;
    }

    private static int fnv1a(byte[] bytes) {
        int hash = 0x811c9dc5;
        for (byte b : bytes) {
            hash ^= (b & 0xff);
            hash *= 0x01000193;
        }
        return hash;
    }

    private static int djb2(byte[] bytes) {
        int hash = 5381;
        for (byte b : bytes) {
            hash = (hash * 33) ^ (b & 0xff);
        }
        return hash;
    }

    /** Serialized form: {@code [numBits:4][numHashes:4][bitsLen:4][bits]}. */
    public byte[] serialize() {
        byte[] bitBytes = bits.toByteArray();
        ByteBuffer buf = ByteBuffer.allocate(12 + bitBytes.length);
        buf.putInt(numBits).putInt(numHashes).putInt(bitBytes.length).put(bitBytes);
        return buf.array();
    }

    public static BloomFilter deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        int numBits = buf.getInt();
        int numHashes = buf.getInt();
        int bitsLen = buf.getInt();
        byte[] bitBytes = new byte[bitsLen];
        buf.get(bitBytes);
        return new BloomFilter(numBits, numHashes, BitSet.valueOf(bitBytes));
    }
}
