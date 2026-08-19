package corekv;

import corekv.cache.LruCache;
import corekv.trie.Trie;
import corekv.wal.WalRecord;
import corekv.wal.WriteAheadLog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The LRU cache IS the store: once the number of keys exceeds {@code capacity},
 * the least-recently-used key/value is evicted, along with its trie entry and a
 * matching WAL delete record. {@code get} counts as a use and refreshes recency,
 * so it takes the write lock rather than the read lock.
 */
public class CoreKVStore {
    private final LruCache<String, String> store;
    private final Trie trie;
    private final WriteAheadLog wal;
    private final ReentrantReadWriteLock lock;

    public CoreKVStore(int capacity, Path walPath) throws IOException {
        this.store = new LruCache<>(capacity);
        this.trie = new Trie();
        this.wal = new WriteAheadLog(walPath);
        this.lock = new ReentrantReadWriteLock();
        replayWal();
    }

    public String get(String key) {
        lock.writeLock().lock();
        try {
            return store.get(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public PutResult put(String key, String value) throws IOException {
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
            return store.containsKey(key);
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

    public List<LruCache.Entry<String, String>> snapshot() {
        lock.readLock().lock();
        try {
            return store.entries();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return store.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() throws IOException {
        lock.writeLock().lock();
        try {
            store.clear();
            trie.clear();
            wal.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Path walPath() {
        return wal.path();
    }

    private PutResult applyPut(String key, String value) throws IOException {
        LruCache.PutOutcome<String, String> outcome = store.put(key, value);
        trie.insert(key);
        if (outcome.evicted()) {
            trie.remove(outcome.evictedKey());
            wal.appendDelete(outcome.evictedKey());
            return new PutResult(outcome.previousValue(), outcome.evictedKey());
        }
        return new PutResult(outcome.previousValue(), null);
    }

    private String applyDelete(String key) {
        String removed = store.remove(key);
        if (removed != null) {
            trie.remove(key);
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

    /** Outcome of a {@link #put(String, String)}: the prior value, and which key (if any) was evicted to make room. */
    public static final class PutResult {
        private final String previousValue;
        private final String evictedKey;

        private PutResult(String previousValue, String evictedKey) {
            this.previousValue = previousValue;
            this.evictedKey = evictedKey;
        }

        public String previousValue() {
            return previousValue;
        }

        public boolean evicted() {
            return evictedKey != null;
        }

        public String evictedKey() {
            return evictedKey;
        }
    }
}
