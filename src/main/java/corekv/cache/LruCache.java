package corekv.cache;

import corekv.hash.CustomHashTable;

import java.util.ArrayList;
import java.util.List;

public class LruCache<K, V> {
    private final int capacity;
    private final CustomHashTable<K, Node<K, V>> index;
    private Node<K, V> head;
    private Node<K, V> tail;
    private int size;

    public LruCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive.");
        }
        this.capacity = capacity;
        this.index = new CustomHashTable<>(capacity, 0.75);
    }

    public V get(K key) {
        Node<K, V> node = index.get(key);
        if (node == null) {
            return null;
        }
        moveToFront(node);
        return node.value;
    }

    public V peek(K key) {
        Node<K, V> node = index.get(key);
        return node == null ? null : node.value;
    }

    /**
     * Inserts or updates a key. If the update pushes the cache past capacity,
     * the least-recently-used entry is evicted and reported on the returned
     * outcome so the caller can keep other structures (indexes, logs) in sync.
     */
    public PutOutcome<K, V> put(K key, V value) {
        Node<K, V> existing = index.get(key);
        if (existing != null) {
            V previous = existing.value;
            existing.value = value;
            moveToFront(existing);
            return new PutOutcome<>(previous, null, null);
        }

        Node<K, V> node = new Node<>(key, value);
        attachAtFront(node);
        index.put(key, node);
        size++;

        if (size > capacity) {
            Entry<K, V> evicted = evictLeastRecentlyUsed();
            return new PutOutcome<>(null, evicted.key(), evicted.value());
        }
        return new PutOutcome<>(null, null, null);
    }

    public V remove(K key) {
        Node<K, V> node = index.remove(key);
        if (node == null) {
            return null;
        }
        detach(node);
        size--;
        return node.value;
    }

    public boolean containsKey(K key) {
        return index.containsKey(key);
    }

    public int size() {
        return size;
    }

    public void clear() {
        index.clear();
        head = null;
        tail = null;
        size = 0;
    }

    public List<K> keysInOrder() {
        List<K> keys = new ArrayList<>(size);
        Node<K, V> current = head;
        while (current != null) {
            keys.add(current.key);
            current = current.next;
        }
        return keys;
    }

    /** Returns entries ordered from most-recently-used to least-recently-used. */
    public List<Entry<K, V>> entries() {
        List<Entry<K, V>> result = new ArrayList<>(size);
        Node<K, V> current = head;
        while (current != null) {
            result.add(new Entry<>(current.key, current.value));
            current = current.next;
        }
        return result;
    }

    private Entry<K, V> evictLeastRecentlyUsed() {
        Entry<K, V> evicted = new Entry<>(tail.key, tail.value);
        detach(tail);
        index.remove(evicted.key());
        size--;
        return evicted;
    }

    private void moveToFront(Node<K, V> node) {
        if (node == head) {
            return;
        }
        detach(node);
        attachAtFront(node);
    }

    private void attachAtFront(Node<K, V> node) {
        node.previous = null;
        node.next = head;
        if (head != null) {
            head.previous = node;
        }
        head = node;
        if (tail == null) {
            tail = node;
        }
    }

    private void detach(Node<K, V> node) {
        if (node.previous != null) {
            node.previous.next = node.next;
        } else {
            head = node.next;
        }
        if (node.next != null) {
            node.next.previous = node.previous;
        } else {
            tail = node.previous;
        }
        node.previous = null;
        node.next = null;
    }

    private static final class Node<K, V> {
        private final K key;
        private V value;
        private Node<K, V> previous;
        private Node<K, V> next;

        private Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
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

    /**
     * Result of a {@link #put(Object, Object)} call: the previous value for the key
     * (if it was already present), and the key/value that got evicted to make room
     * (if the cache was over capacity). At most one of "previous value" and
     * "eviction" applies to a single put, since an update never triggers eviction.
     */
    public static final class PutOutcome<K, V> {
        private final V previousValue;
        private final K evictedKey;
        private final V evictedValue;

        private PutOutcome(V previousValue, K evictedKey, V evictedValue) {
            this.previousValue = previousValue;
            this.evictedKey = evictedKey;
            this.evictedValue = evictedValue;
        }

        public V previousValue() {
            return previousValue;
        }

        public boolean evicted() {
            return evictedKey != null;
        }

        public K evictedKey() {
            return evictedKey;
        }

        public V evictedValue() {
            return evictedValue;
        }
    }
}
