package com.gladunalexander.lsmkv.trie;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A compressed radix (PATRICIA) trie mapping string keys to string values.
 *
 * <p>It backs the memtable: keys are kept in sorted order, so in-order traversal yields a
 * sorted flush and supports range scans without a separate sort. Edges are labelled with
 * substrings (not single characters) so chains of single-child nodes are collapsed.
 *
 * <p>A {@code null} value marks a tombstone. Deletes never remove nodes; they store a
 * tombstone at the key, matching the LSM model where deletes are just another record.
 */
public class RadixTrie {

    private static final class Node {
        String edge;                 // label on the edge leading into this node
        boolean terminal;            // does a key end here
        String value;                // value if terminal (null = tombstone)
        final TreeMap<Character, Node> children = new TreeMap<>();

        Node(String edge) {
            this.edge = edge;
        }
    }

    private final Node root = new Node("");
    private int size; // number of distinct keys (including tombstones)

    public void put(String key, String value) {
        insert(root, key, value);
    }

    private void insert(Node node, String suffix, String value) {
        if (suffix.isEmpty()) {
            markTerminal(node, value);
            return;
        }
        char c = suffix.charAt(0);
        Node child = node.children.get(c);
        if (child == null) {
            Node leaf = new Node(suffix);
            markTerminal(leaf, value);
            node.children.put(c, leaf);
            return;
        }

        int common = commonPrefixLength(child.edge, suffix);
        if (common == child.edge.length()) {
            insert(child, suffix.substring(common), value); // edge fully matched, descend
            return;
        }

        // Split the child's edge at the common prefix.
        Node mid = new Node(child.edge.substring(0, common));
        node.children.put(c, mid);
        child.edge = child.edge.substring(common);
        mid.children.put(child.edge.charAt(0), child);

        String rest = suffix.substring(common);
        if (rest.isEmpty()) {
            markTerminal(mid, value);
        } else {
            Node leaf = new Node(rest);
            markTerminal(leaf, value);
            mid.children.put(rest.charAt(0), leaf);
        }
    }

    private void markTerminal(Node node, String value) {
        if (!node.terminal) {
            size++;
        }
        node.terminal = true;
        node.value = value;
    }

    /** Exact lookup: empty if absent, otherwise the value ({@code null} = tombstone). */
    public Optional<String> lookup(String key) {
        Node node = root;
        String suffix = key;
        while (true) {
            if (suffix.isEmpty()) {
                return node.terminal ? Optional.ofNullable(node.value) : Optional.empty();
            }
            Node child = node.children.get(suffix.charAt(0));
            if (child == null || !suffix.startsWith(child.edge)) {
                return Optional.empty();
            }
            suffix = suffix.substring(child.edge.length());
            node = child;
        }
    }

    /** True if the key is present (as a live value or a tombstone). */
    public boolean contains(String key) {
        Node node = root;
        String suffix = key;
        while (!suffix.isEmpty()) {
            Node child = node.children.get(suffix.charAt(0));
            if (child == null || !suffix.startsWith(child.edge)) {
                return false;
            }
            suffix = suffix.substring(child.edge.length());
            node = child;
        }
        return node.terminal;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** All entries in sorted key order ({@code null} value = tombstone). */
    public LinkedHashMap<String, String> entries() {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        traverse(root, "", out);
        return out;
    }

    private void traverse(Node node, String prefix, Map<String, String> out) {
        String full = prefix + node.edge;
        if (node.terminal) {
            out.put(full, node.value); // node's key sorts before any descendant's key
        }
        for (Node child : node.children.values()) { // TreeMap => sorted by first char
            traverse(child, full, out);
        }
    }

    private static int commonPrefixLength(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }
}
