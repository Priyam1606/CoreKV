package corekv;

import corekv.cache.LruCache;
import corekv.hash.CustomHashTable;
import corekv.trie.Trie;
import corekv.wal.WalRecord;
import corekv.wal.WriteAheadLog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CoreKVStore {
    private final CustomHashTable<String, String> storage;
    private final LruCache<String, String> cache;
    private final Trie trie;
    private final WriteAheadLog wal;
    private final ReentrantReadWriteLock lock;

    public CoreKVStore(int initialCapacity, int cacheCapacity, Path walPath) throws IOException {
        this.storage = new CustomHashTable<>(initialCapacity, 0.75);
        this.cache = new LruCache<>(cacheCapacity);
        this.trie = new Trie();
        this.wal = new WriteAheadLog(walPath);
        this.lock = new ReentrantReadWriteLock();
        replayWal();
    }

    public String get(String key) {
        lock.readLock().lock();
        try {
            String cached = cache.peek(key);
            if (cached != null) {
                return refreshCacheEntry(key, cached);
            }

            String value = storage.get(key);
            if (value == null) {
                return null;
            }
            return refreshCacheEntry(key, value);
        } finally {
            lock.readLock().unlock();
        }
    }

    public String put(String key, String value) throws IOException {
        validateKey(key);
        validateValue(value);
        lock.writeLock().lock();
        try {
            wal.appendPut(key, value);
            return applyPut(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String delete(String key) throws IOException {
        validateKey(key);
        lock.writeLock().lock();
        try {
            wal.appendDelete(key);
            return applyDelete(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean containsKey(String key) {
        lock.readLock().lock();
        try {
            return storage.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<String> keysWithPrefix(String prefix) {
        lock.readLock().lock();
        try {
            return trie.keysWithPrefix(prefix);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<CustomHashTable.Entry<String, String>> snapshot() {
        lock.readLock().lock();
        try {
            return storage.entries();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return storage.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() throws IOException {
        lock.writeLock().lock();
        try {
            storage.clear();
            cache.clear();
            trie.clear();
            wal.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Path walPath() {
        return wal.path();
    }

    private String refreshCacheEntry(String key, String fallbackValue) {
        lock.readLock().unlock();
        lock.writeLock().lock();
        try {
            String cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            String current = storage.get(key);
            if (current == null) {
                return null;
            }
            cache.put(key, current);
            return current;
        } finally {
            lock.readLock().lock();
            lock.writeLock().unlock();
        }
    }

    private String applyPut(String key, String value) {
        String previous = storage.put(key, value);
        trie.insert(key);
        cache.put(key, value);
        return previous;
    }

    private String applyDelete(String key) {
        String removed = storage.remove(key);
        if (removed != null) {
            trie.remove(key);
            cache.remove(key);
        }
        return removed;
    }

    private void replayWal() throws IOException {
        lock.writeLock().lock();
        try {
            for (WalRecord record : wal.replay()) {
                if (record.operation() == WalRecord.Operation.PUT) {
                    applyPut(record.key(), record.value());
                } else {
                    applyDelete(record.key());
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key must not be null or empty.");
        }
    }

    private void validateValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null.");
        }
    }
}
