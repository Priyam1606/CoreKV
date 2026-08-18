package corekv;

import corekv.cache.LruCache;
import corekv.hash.CustomHashTable;
import corekv.trie.Trie;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class CoreKVStoreTest {
    public static void main(String[] args) throws Exception {
        testCustomHashTableResizesAndHandlesCollisions();
        testLruCacheEvictsLeastRecentlyUsed();
        testTriePrefixQueries();
        testWalRecovery();
        testClearResetsStateAndWal();
        testConcurrentAccessSmoke();
        System.out.println("All CoreKV tests passed.");
    }

    private static void testCustomHashTableResizesAndHandlesCollisions() {
        CustomHashTable<CollisionKey, String> table = new CustomHashTable<>(2, 0.75);
        table.put(new CollisionKey("one"), "1");
        table.put(new CollisionKey("two"), "2");
        table.put(new CollisionKey("three"), "3");

        assertEquals("1", table.get(new CollisionKey("one")), "Hash table should retrieve first colliding key.");
        assertEquals("2", table.get(new CollisionKey("two")), "Hash table should retrieve second colliding key.");
        assertEquals(3, table.size(), "Hash table should preserve size after resize.");
    }

    private static void testLruCacheEvictsLeastRecentlyUsed() {
        LruCache<String, String> cache = new LruCache<>(2);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.get("a");
        cache.put("c", "3");

        assertTrue(cache.containsKey("a"), "Most recently used key should remain in cache.");
        assertTrue(!cache.containsKey("b"), "Least recently used key should be evicted.");
        assertEquals(List.of("c", "a"), cache.keysInOrder(), "Cache order should be most-recent to least-recent.");
    }

    private static void testTriePrefixQueries() {
        Trie trie = new Trie();
        trie.insert("user:1");
        trie.insert("user:2");
        trie.insert("config:theme");

        assertTrue(trie.contains("user:1"), "Trie should contain inserted key.");
        assertTrue(trie.startsWith("user:"), "Trie should support prefix lookups.");
        assertEquals(2, trie.keysWithPrefix("user:").size(), "Trie should return keys under a prefix.");

        trie.remove("user:1");
        assertTrue(!trie.contains("user:1"), "Trie remove should delete exact key.");
    }

    private static void testWalRecovery() throws Exception {
        Path tempDirectory = Files.createTempDirectory("corekv-test");
        Path walPath = tempDirectory.resolve("corekv.wal");

        CoreKVStore initialStore = new CoreKVStore(4, 2, walPath);
        initialStore.put("user:1", "Asha");
        initialStore.put("user:2", "Mira");
        initialStore.delete("user:2");

        CoreKVStore recoveredStore = new CoreKVStore(4, 2, walPath);
        assertEquals("Asha", recoveredStore.get("user:1"), "Recovered store should replay PUT records.");
        assertEquals(null, recoveredStore.get("user:2"), "Recovered store should replay DELETE records.");
    }

    private static void testClearResetsStateAndWal() throws Exception {
        Path tempDirectory = Files.createTempDirectory("corekv-clear");
        Path walPath = tempDirectory.resolve("corekv.wal");

        CoreKVStore store = new CoreKVStore(4, 2, walPath);
        store.put("user:1", "Asha");
        store.put("user:2", "Rohan");
        store.clear();

        assertEquals(0, store.size(), "Clear should remove all in-memory entries.");
        assertEquals(List.of(), store.keysWithPrefix("user:"), "Clear should reset trie-based lookups.");

        CoreKVStore recoveredStore = new CoreKVStore(4, 2, walPath);
        assertEquals(0, recoveredStore.size(), "Clear should truncate the WAL so recovery starts empty.");
    }

    private static void testConcurrentAccessSmoke() throws Exception {
        Path tempDirectory = Files.createTempDirectory("corekv-concurrency");
        CoreKVStore store = new CoreKVStore(8, 4, tempDirectory.resolve("corekv.wal"));
        int threadCount = 6;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            Thread thread = new Thread(() -> {
                try {
                    ready.countDown();
                    start.await();
                    for (int iteration = 0; iteration < 50; iteration++) {
                        String key = "thread:" + index + ":" + iteration;
                        store.put(key, "value:" + iteration);
                        store.get(key);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
            thread.start();
        }

        ready.await();
        start.countDown();
        done.await();

        assertEquals(threadCount * 50, store.size(), "Concurrent writes should preserve all inserted keys.");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if ((expected == null && actual != null) || (expected != null && !expected.equals(actual))) {
            throw new AssertionError(message + " Expected: " + expected + ", actual: " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record CollisionKey(String value) {
        @Override
        public int hashCode() {
            return 42;
        }
    }
}
