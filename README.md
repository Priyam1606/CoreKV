# CoreKV

CoreKV is a from-scratch Java key-value store project built to demonstrate core data-structure and systems-design concepts without relying on `HashMap` or cache libraries for the main storage engine.

It combines a custom hash table, an LRU cache, a trie for prefix search, thread-safe access, and write-ahead logging into one coherent DSA-focused project.

## Features

- Custom hash table with chaining-based collision resolution
- Dynamic resizing and rehashing based on load factor
- O(1) average-case `put`, `get`, and `delete`
- LRU cache built using a handwritten doubly linked list plus the custom hash table, used as the store itself: once the key count exceeds capacity, the least-recently-used key is actually evicted, not just dropped from a side index
- Trie-based prefix search for string keys
- Thread safety using `ReentrantReadWriteLock`
- Write-ahead log for crash recovery
- Guided demo, REPL mode, and large-scale simulation mode

## Why This Project Is Good

The main logic is implemented by hand:

- Hashing and collision resolution
- Bucket-array management
- Rehashing during resize
- Doubly linked list operations for LRU
- Trie node traversal and prefix collection
- WAL replay and recovery flow

Java standard utilities are used only for support tasks such as file I/O, locking, console input, and simple result containers.

## Project Structure

```text
CoreKV/
├── src/
│   ├── main/java/corekv/
│   │   ├── cache/
│   │   │   └── LruCache.java
│   │   ├── hash/
│   │   │   └── CustomHashTable.java
│   │   ├── trie/
│   │   │   └── Trie.java
│   │   ├── wal/
│   │   │   ├── WalRecord.java
│   │   │   └── WriteAheadLog.java
│   │   ├── CoreKVStore.java
│   │   └── Main.java
│   └── test/java/corekv/
│       └── CoreKVStoreTest.java
├── scripts/
│   ├── build.ps1
│   ├── run-demo.ps1
│   ├── run-simulation-check.ps1
│   └── run-tests.ps1
└── README.md
```

## Core Components

### 1. Custom Hash Table

Implemented in `CustomHashTable.java`.

- Uses an array of buckets
- Handles collisions through separate chaining
- Resizes when the load factor crosses the threshold, rehashing nodes in place (each node keeps its identity and is just re-bucketed) rather than reinserting through `put`
- Hashes `String` keys itself with a hand-rolled FNV-1a implementation instead of delegating to `String.hashCode()` — the actual "turn this key into a number" step is from scratch, not just the table built on top of it. Non-`String` keys (e.g. `Character` for trie children) fall back to `hashCode()`, since the table is generic and can't hand-hash a type it knows nothing about
- Spreads that hash the same way `java.util.HashMap` does (`h ^ (h >>> 16)`) so keys that differ mainly in their high bits still land in different buckets on a small table
- Threads every live entry through an intrusive doubly linked list, `LinkedHashMap`-style, so iterating all entries (`entries()`, or a `for` loop via `Iterable<Entry<K, V>>`) costs O(size) instead of O(bucket array length) — the array can be much larger than the occupied slot count

### 2. LRU Cache

Implemented in `LruCache.java`.

- Uses a custom doubly linked list
- Tracks most recently used and least recently used entries
- Supports O(1) updates and eviction
- `CoreKVStore` uses it as the primary store, not a look-aside cache in front of one: `put` reports back the evicted key/value (if any) via `LruCache.PutOutcome`, and `CoreKVStore` uses that to also drop the key from the trie and append a WAL delete for it. `get` counts as a "use" and moves the entry to the front, so it takes the write lock instead of the read lock — reads and writes are no longer concurrent with each other, which is the trade-off for correct recency tracking.

### 3. Trie

Implemented in `Trie.java`.

- Supports prefix-based lookup
- Useful for queries like all keys starting with `priyam` or `user:`
- Each node's children are a `CustomHashTable<Character, TrieNode>` (not a fixed-size array), so the trie isn't limited to a fixed alphabet — any Unicode key works. Collecting matches iterates each node's children directly through `CustomHashTable`'s `Iterable`, so cost is proportional to actual children visited, not to that table's bucket array size

### 4. Thread-Safe Store

Implemented in `CoreKVStore.java`.

- Uses `ReentrantReadWriteLock`
- Allows multiple concurrent readers
- Allows only one writer at a time

### 5. Write-Ahead Log

Implemented in `WriteAheadLog.java`.

- Every write is appended to disk before being applied in memory
- On restart, the log is replayed to rebuild the store state
- LRU evictions append a `DELETE` record too, so a restored store doesn't resurrect a key that was actually evicted. Caveat: `get` calls aren't logged, so a replay only sees insertion order, not read-driven recency — the recovered store's *key set* matches what was live (each real eviction is explicitly replayed as a delete), but which key would be evicted next after recovery can differ from the original process if reads had reordered recency before a crash.

## How To Run

### Start Here

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\start.ps1
```

Builds the project and drops you straight into the interactive CLI: it asks for a store capacity, then you're at a `>` prompt to `put`/`get`/`delete`/`prefix`/`show` your own data. Type `help` any time to see the command list again. This is the same thing as [REPL Mode](#repl-mode) below, just as a single command with nothing to remember.

Every session starts from a clean slate and wipes itself on the way out: the store (and its WAL file) is cleared the moment you connect, and cleared again when you type `exit` (or just close the terminal input) — so nothing carries over between runs, no matter what a previous session left behind.

### Build

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\build.ps1
```

### Guided Demo

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\build.ps1
java -cp out/main corekv.Main
```

This mode asks for a store capacity, then how many key-value pairs you want to insert up front (inserting more than the capacity evicts the least-recently-used key as you go), and then lets you keep querying the store. Like the REPL, it starts clean and clears itself on `exit`.

- `get <key>`
- `put <key> <value>`
- `delete <key>`
- `prefix <prefix>`
- `size`
- `show`
- `help`
- `clear`
- `exit`

### Auto Demo

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\run-demo.ps1
```

### REPL Mode

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\build.ps1
java -cp out/main corekv.Main repl
```

Same experience as `start.ps1` above, just without the wrapper script — useful if you want to build once and re-run without rebuilding each time. Asks for a store capacity up front, then it's a free-form `put`/`get`/`delete`/`prefix`/`size`/`show`/`help`/`clear`/`exit` prompt. `put` reports back when it had to evict a key to make room, e.g. `OK (capacity reached -> evicted least-recently-used key: user:2)`.

### Simulation Check

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\run-simulation-check.ps1
```

This mode:

- generates a large number of unique key-value pairs
- loads them into CoreKV, sizing the store's LRU capacity to the requested count so nothing gets evicted before it can be verified
- exports a CSV verification sheet inside `data/`
- allows manual verification using commands like:
  - `get <key>`
  - `prefix <prefix>`
  - `verify <key> <expectedValue>`
  - `sheet`
  - `sample <count>`

## Testing

Run:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\run-tests.ps1
```

The test suite covers:

- collision handling and resize behavior
- linked-list iteration staying correct across put/remove/resize/clear
- the hand-rolled string hash being value-based (a separately constructed but equal string finds the same entry)
- LRU eviction logic (within the standalone `LruCache`, and end-to-end through `CoreKVStore` — trie and WAL kept in sync with evictions)
- trie prefix queries
- WAL recovery, including recovery after an eviction
- clear/reset behavior
- concurrent access smoke testing

## Time Complexity

- Hash table `get/put/delete`: O(1) average case (amortized, assuming a reasonable hash distribution)
- Hash table full iteration (`entries()`, `for` loop): O(size), not O(bucket array length)
- Trie prefix lookup: O(prefix length + total size of the matched keys) — optimal, since every matching key has to be materialized character by character
- LRU cache access/update/eviction: O(1)

## Notes

- Keys and values are currently stored as strings
- The store's capacity is a hard cap: once it's full, every `put` of a new key evicts the least-recently-used existing key. `CoreKVStore(int capacity, Path walPath)` takes that single capacity — the REPL and guided demo ask for it interactively at startup, the auto demo defaults to 16, and the simulation check sizes it to the requested row count so it can verify data instead of demonstrating eviction
- The REPL and guided demo are intentionally ephemeral: each session clears the store (and its WAL file) both when it starts and when it exits, so nothing from a previous run lingers. The WAL replay-on-crash mechanism itself is still fully implemented and tested at the `CoreKVStore` level (see `testWalRecovery`) — the interactive modes just choose not to carry state across runs
- WAL files and simulation CSV outputs are generated under `data/`
- Build outputs are generated under `out/`
- Generated artifacts are excluded from git using `.gitignore`
