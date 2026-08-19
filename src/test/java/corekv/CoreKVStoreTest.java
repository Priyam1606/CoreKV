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
        testCustomHashTableIterationStaysConsistent();
        testHandRolledStringHashIsValueBased();
        testLruCacheEvictsLeastRecentlyUsed();
        testTriePrefixQueries();
        testWalRecovery();
        testClearResetsStateAndWal();
        testStoreEvictsLeastRecentlyUsedAcrossTrieAndWal();
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

    private static void testCustomHashTableIterationStaysConsistent() {
        CustomHashTable<String, Integer> table = new CustomHashTable<>(2, 0.75);
        table.put("a", 1);
        table.put("b", 2);
        table.put("c", 3);
        table.put("d", 4);

        assertEquals(4, table.entries().size(), "entries() should list every live key after growth-triggering resizes.");

        table.remove("b");
        List<String> keysAfterRemove = table.entries().stream().map(CustomHashTable.Entry::key).sorted().toList();
        assertEquals(List.of("a", "c", "d"), keysAfterRemove, "entries() should drop a removed key and keep the rest.");

        table.put("b", 20);
        assertEquals(Integer.valueOf(20), table.get("b"), "Re-inserting a removed key should work after unlinking it.");
        assertEquals(4, table.entries().size(), "entries() should include a key re-inserted after removal.");

        table.clear();
        assertEquals(List.of(), table.entries(), "entries() should be empty after clear().");
    }

    private static void testHandRolledStringHashIsValueBased() {
        CustomHashTable<String, String> table = new CustomHashTable<>(4, 0.75);
        table.put("user:1", "Asha");

        // Built via char[] specifically so this is a genuinely different String
        // object, not the same interned literal — proving the hash table finds
        // it by *content*, not by object identity.
        String separatelyConstructedKey = new String("user:1".toCharArray());
        assertEquals("Asha", table.get(separatelyConstructedKey),
            "A separately constructed but equal string must hash to the same bucket as the original.");

        table.put(separatelyConstructedKey, "Updated");
        assertEquals(1, table.size(), "Hashing by value means this is an update, not a second entry.");
        assertEquals("Updated", table.get("user:1"), "Update through the copy should be visible through the original key.");
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

        CoreKVStore initialStore = new CoreKVStore(4, walPath);
        initialStore.put("user:1", "Asha");
        initialStore.put("user:2", "Mira");
        initialStore.delete("user:2");

        CoreKVStore recoveredStore = new CoreKVStore(4, walPath);
        assertEquals("Asha", recoveredStore.get("user:1"), "Recovered store should replay PUT records.");
        assertEquals(null, recoveredStore.get("user:2"), "Recovered store should replay DELETE records.");
    }

    private static void testClearResetsStateAndWal() throws Exception {
        Path tempDirectory = Files.createTempDirectory("corekv-clear");
        Path walPath = tempDirectory.resolve("corekv.wal");

        CoreKVStore store = new CoreKVStore(4, walPath);
        store.put("user:1", "Asha");
        store.put("user:2", "Rohan");
        store.clear();

        assertEquals(0, store.size(), "Clear should remove all in-memory entries.");
        assertEquals(List.of(), store.keysWithPrefix("user:"), "Clear should reset trie-based lookups.");

        CoreKVStore recoveredStore = new CoreKVStore(4, walPath);
        assertEquals(0, recoveredStore.size(), "Clear should truncate the WAL so recovery starts empty.");
    }

    private static void testStoreEvictsLeastRecentlyUsedAcrossTrieAndWal() throws Exception {
        Path tempDirectory = Files.createTempDirectory("corekv-evict");
        Path walPath = tempDirectory.resolve("corekv.wal");

        CoreKVStore store = new CoreKVStore(2, walPath);
        assertTrue(!store.put("user:1", "Asha").evicted(), "Inserting under capacity should not evict anything.");
        store.put("user:2", "Mira");
        store.get("user:1");
        CoreKVStore.PutResult result = store.put("user:3", "Kabir");

        assertTrue(result.evicted(), "PutResult should report that this insert evicted a key.");
        assertEquals("user:2", result.evictedKey(), "PutResult should name the actual least-recently-used key.");
        assertEquals(2, store.size(), "Store should stay at capacity once it is full.");
        assertEquals("Asha", store.get("user:1"), "Recently used key should survive eviction.");
        assertEquals("Kabir", store.get("user:3"), "Newly inserted key should be present.");
        assertEquals(null, store.get("user:2"), "Least recently used key should be evicted.");
        assertTrue(!store.containsKey("user:2"), "Evicted key should no longer be reachable.");
        assertEquals(List.of(), store.keysWithPrefix("user:2"), "Evicted key should be removed from the trie too.");

        CoreKVStore recoveredStore = new CoreKVStore(2, walPath);
        assertTrue(!recoveredStore.containsKey("user:2"), "Recovery should not resurrect an evicted key.");
    }

    private static void testConcurrentAccessSmoke() throws Exception {
        Path tempDirectory = Files.createTempDirectory("corekv-concurrency");
        int threadCount = 6;
        // Sized above threadCount * 50 so eviction never kicks in here; this test is
        // about concurrent access safety, not eviction (that's covered separately).
        CoreKVStore store = new CoreKVStore(threadCount * 50 + 10, tempDirectory.resolve("corekv.wal"));
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
