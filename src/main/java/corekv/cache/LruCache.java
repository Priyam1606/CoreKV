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
        this.index = new CustomHashTable<>();
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

    public void put(K key, V value) {
        Node<K, V> existing = index.get(key);
        if (existing != null) {
            existing.value = value;
            moveToFront(existing);
            return;
        }

        Node<K, V> node = new Node<>(key, value);
        attachAtFront(node);
        index.put(key, node);
        size++;

        if (size > capacity) {
            evictLeastRecentlyUsed();
        }
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

    private void evictLeastRecentlyUsed() {
        if (tail == null) {
            return;
        }
        K keyToRemove = tail.key;
        detach(tail);
        index.remove(keyToRemove);
        size--;
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
}
