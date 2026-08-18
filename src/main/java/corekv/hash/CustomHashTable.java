package corekv.hash;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CustomHashTable<K, V> {
    private static final int DEFAULT_CAPACITY = 16;
    private static final double DEFAULT_LOAD_FACTOR = 0.75;

    private final double loadFactorThreshold;
    private Node<K, V>[] buckets;
    private int size;

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

        buckets[index] = new Node<>(key, value, buckets[index]);
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
    }

    public List<Entry<K, V>> entries() {
        List<Entry<K, V>> result = new ArrayList<>(size);
        for (Node<K, V> bucket : buckets) {
            Node<K, V> current = bucket;
            while (current != null) {
                result.add(new Entry<>(current.key, current.value));
                current = current.next;
            }
        }
        return result;
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

    @SuppressWarnings("unchecked")
    private void resize() {
        Node<K, V>[] oldBuckets = buckets;
        buckets = (Node<K, V>[]) new Node[oldBuckets.length * 2];
        size = 0;

        for (Node<K, V> bucket : oldBuckets) {
            Node<K, V> current = bucket;
            while (current != null) {
                put(current.key, current.value);
                current = current.next;
            }
        }
    }

    private int bucketIndex(K key, int length) {
        return Math.floorMod(key == null ? 0 : key.hashCode(), length);
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

        private Node(K key, V value, Node<K, V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }
}
