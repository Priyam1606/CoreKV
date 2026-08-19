package corekv.hash;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * A hash table with separate chaining, dynamic resizing, and an intrusive
 * doubly linked list threaded through every live entry (the same technique
 * {@code java.util.LinkedHashMap} uses). The linked list means iterating all
 * entries costs O(size), not O(bucket array length) — a plain array scan
 * would waste work on empty buckets, which matters here because this table
 * also backs sparse structures like a trie node's children.
 */
public class CustomHashTable<K, V> implements Iterable<CustomHashTable.Entry<K, V>> {
    private static final int DEFAULT_CAPACITY = 16;
    private static final double DEFAULT_LOAD_FACTOR = 0.75;
    private static final int FNV_OFFSET_BASIS = 0x811C9DC5;
    private static final int FNV_PRIME = 0x01000193;

    private final double loadFactorThreshold;
    private Node<K, V>[] buckets;
    private int size;
    private Node<K, V> head;
    private Node<K, V> tail;

    @SuppressWarnings("unchecked")
    public CustomHashTable() {
        this.buckets = (Node<K, V>[]) new Node[DEFAULT_CAPACITY];
        this.loadFactorThreshold = DEFAULT_LOAD_FACTOR;
    }

    @SuppressWarnings("unchecked")
    public CustomHashTable(int initialCapacity, double loadFactorThreshold) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("Initial capacity must be positive.");
        }
        if (loadFactorThreshold <= 0.0) {
            throw new IllegalArgumentException("Load factor threshold must be positive.");
        }
        this.buckets = (Node<K, V>[]) new Node[tableSizeFor(initialCapacity)];
        this.loadFactorThreshold = loadFactorThreshold;
    }

    public V get(K key) {
        Node<K, V> node = findNode(key);
        return node == null ? null : node.value;
    }

    public boolean containsKey(K key) {
        return findNode(key) != null;
    }

    public V put(K key, V value) {
        ensureCapacityForInsert();
        int index = bucketIndex(key, buckets.length);
        Node<K, V> current = buckets[index];
        while (current != null) {
            if (Objects.equals(current.key, key)) {
                V oldValue = current.value;
                current.value = value;
                return oldValue;
            }
            current = current.next;
        }

        Node<K, V> node = new Node<>(key, value, buckets[index]);
        buckets[index] = node;
        linkAtTail(node);
        size++;
        return null;
    }

    public V remove(K key) {
        int index = bucketIndex(key, buckets.length);
        Node<K, V> current = buckets[index];
        Node<K, V> previous = null;
        while (current != null) {
            if (Objects.equals(current.key, key)) {
                if (previous == null) {
                    buckets[index] = current.next;
                } else {
                    previous.next = current.next;
                }
                unlink(current);
                size--;
                return current.value;
            }
            previous = current;
            current = current.next;
        }
        return null;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    @SuppressWarnings("unchecked")
    public void clear() {
        buckets = (Node<K, V>[]) new Node[buckets.length];
        size = 0;
        head = null;
        tail = null;
    }

    /** O(size): walks the linked list, never the (possibly much larger) bucket array. */
    public List<Entry<K, V>> entries() {
        List<Entry<K, V>> result = new ArrayList<>(size);
        for (Entry<K, V> entry : this) {
            result.add(entry);
        }
        return result;
    }

    @Override
    public Iterator<Entry<K, V>> iterator() {
        return new Iterator<>() {
            private Node<K, V> current = head;

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public Entry<K, V> next() {
                if (current == null) {
                    throw new NoSuchElementException();
                }
                Entry<K, V> entry = new Entry<>(current.key, current.value);
                current = current.after;
                return entry;
            }
        };
    }

    private Node<K, V> findNode(K key) {
        int index = bucketIndex(key, buckets.length);
        Node<K, V> current = buckets[index];
        while (current != null) {
            if (Objects.equals(current.key, key)) {
                return current;
            }
            current = current.next;
        }
        return null;
    }

    private void ensureCapacityForInsert() {
        double projectedLoadFactor = (double) (size + 1) / buckets.length;
        if (projectedLoadFactor > loadFactorThreshold) {
            resize();
        }
    }

    /**
     * Doubles the bucket array and re-buckets existing nodes in place: each node
     * keeps its identity (and therefore its linked-list position), only its
     * bucket-chain pointer is rewired. This is O(size) with no rehash lookups or
     * reinsertion overhead, unlike routing every entry back through {@link #put}.
     */
    @SuppressWarnings("unchecked")
    private void resize() {
        Node<K, V>[] oldBuckets = buckets;
        Node<K, V>[] newBuckets = (Node<K, V>[]) new Node[oldBuckets.length * 2];

        for (Node<K, V> bucket : oldBuckets) {
            Node<K, V> current = bucket;
            while (current != null) {
                Node<K, V> next = current.next;
                int index = bucketIndex(current.key, newBuckets.length);
                current.next = newBuckets[index];
                newBuckets[index] = current;
                current = next;
            }
        }

        buckets = newBuckets;
    }

    private void linkAtTail(Node<K, V> node) {
        node.before = tail;
        node.after = null;
        if (tail != null) {
            tail.after = node;
        }
        tail = node;
        if (head == null) {
            head = node;
        }
    }

    private void unlink(Node<K, V> node) {
        if (node.before != null) {
            node.before.after = node.after;
        } else {
            head = node.after;
        }
        if (node.after != null) {
            node.after.before = node.before;
        } else {
            tail = node.before;
        }
        node.before = null;
        node.after = null;
    }

    private int bucketIndex(K key, int length) {
        return Math.floorMod(key == null ? 0 : spread(rawHash(key)), length);
    }

    /**
     * Computes the raw hash for a key. CoreKV's keys are strings, so this table
     * hashes them itself (FNV-1a) instead of delegating to {@code String.hashCode()}.
     * Any other key type (e.g. {@code Character} for trie children) falls back to
     * {@code hashCode()}, since this table is still generic and can't hand-hash a
     * type it knows nothing about.
     */
    private int rawHash(K key) {
        if (key instanceof String stringKey) {
            return fnv1aHash(stringKey);
        }
        return key.hashCode();
    }

    /**
     * FNV-1a: walk the characters, XOR each one in, multiply by a fixed prime.
     * Simple, well-distributed, and a genuinely from-scratch hash function rather
     * than a borrowed one — chosen over the polynomial scheme
     * {@code String.hashCode()} uses specifically so this isn't the same formula
     * with different constants.
     */
    private static int fnv1aHash(String value) {
        int hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    /**
     * Folds the high bits of a hash code into the low bits, the same trick
     * {@code java.util.HashMap} uses, so that hash codes differing mainly in
     * their upper bits still spread across a small bucket array instead of
     * colliding.
     */
    private static int spread(int hash) {
        return hash ^ (hash >>> 16);
    }

    private int tableSizeFor(int requestedCapacity) {
        int capacity = 1;
        while (capacity < requestedCapacity) {
            capacity <<= 1;
        }
        return capacity;
    }

    public static final class Entry<K, V> {
        private final K key;
        private final V value;

        public Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K key() {
            return key;
        }

        public V value() {
            return value;
        }
    }

    private static final class Node<K, V> {
        private final K key;
        private V value;
        private Node<K, V> next;
        private Node<K, V> before;
        private Node<K, V> after;

        private Node(K key, V value, Node<K, V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }
}
