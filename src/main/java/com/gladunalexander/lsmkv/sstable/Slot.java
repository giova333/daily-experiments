package com.gladunalexander.lsmkv.sstable;

/**
 * The result of looking a key up in a single source (memtable or one SSTable).
 *
 * <p>A {@code Slot} distinguishes a live value from a {@code tombstone} (a marker that the
 * key was deleted). Note this is different from "the key is absent in this source": absence
 * is represented by an empty {@link java.util.Optional Optional&lt;Slot&gt;}, so a newer
 * tombstone correctly shadows an older live value.
 */
public record Slot(String value, boolean tombstone) {

    public static Slot of(String value) {
        return new Slot(value, false);
    }

    public static Slot deleted() {
        return new Slot(null, true);
    }
}
