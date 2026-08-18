# CoreKV

CoreKV is a from-scratch Java key-value store project built to demonstrate core data-structure and systems-design concepts without relying on `HashMap` or cache libraries for the main storage engine.

It combines a custom hash table, an LRU cache, a trie for prefix search, thread-safe access, and write-ahead logging into one coherent DSA-focused project.

## Features

- Custom hash table with chaining-based collision resolution
- Dynamic resizing and rehashing based on load factor
- O(1) average-case `put`, `get`, and `delete`
- LRU cache built using a handwritten doubly linked list plus the custom hash table
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
- Resizes when the load factor crosses the threshold

### 2. LRU Cache

Implemented in `LruCache.java`.

- Uses a custom doubly linked list
- Tracks most recently used and least recently used entries
- Supports O(1) updates and eviction

### 3. Trie

Implemented in `Trie.java`.

- Supports prefix-based lookup
- Useful for queries like all keys starting with `priyam` or `user:`

### 4. Thread-Safe Store

Implemented in `CoreKVStore.java`.

- Uses `ReentrantReadWriteLock`
- Allows multiple concurrent readers
- Allows only one writer at a time

### 5. Write-Ahead Log

Implemented in `WriteAheadLog.java`.

- Every write is appended to disk before being applied in memory
- On restart, the log is replayed to rebuild the store state

## How To Run

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

This mode asks how many key-value pairs you want to insert, stores them, and then lets you test:

- `get <key>`
- `put <key> <value>`
- `delete <key>`
- `prefix <prefix>`
- `size`
- `show`
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

### Simulation Check

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\run-simulation-check.ps1
```

This mode:

- generates a large number of unique key-value pairs
- loads them into CoreKV
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
- LRU eviction logic
- trie prefix queries
- WAL recovery
- clear/reset behavior
- concurrent access smoke testing

## Time Complexity

- Hash table `get/put/delete`: O(1) average case
- Trie prefix lookup: O(length of prefix + number of matching keys)
- LRU cache access/update: O(1)

## Notes

- Keys and values are currently stored as strings
- WAL files and simulation CSV outputs are generated under `data/`
- Build outputs are generated under `out/`
- Generated artifacts are excluded from git using `.gitignore`
