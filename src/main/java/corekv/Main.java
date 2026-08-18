package corekv;

import corekv.hash.CustomHashTable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.stream.Stream;

public class Main {
    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final int DEFAULT_CACHE_CAPACITY = 3;
    private static final int SIMULATION_PREFIX_BUCKETS = 1_000;
    private static final String[] FIRST_NAMES = {
        "priyam", "arush", "riya", "kabir", "anaya", "vivaan", "diya", "aditya",
        "kiara", "aarav", "isha", "krish", "meera", "laksh", "myra", "reyansh",
        "siya", "ved", "tara", "yuvan", "avni", "ansh", "pari", "daksh"
    };
    private static final String[] LAST_NAMES = {
        "sharma", "verma", "singh", "gupta", "mehta", "kapoor", "joshi", "nair",
        "iyer", "patel", "reddy", "khan", "malhotra", "saxena", "bansal", "chopra",
        "agrawal", "mishra", "thakur", "sethi", "roy", "desai", "paliwal", "arora"
    };

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "simulation-check".equalsIgnoreCase(args[0])) {
            runSimulationCheck();
            return;
        }

        if (args.length > 0 && "auto-demo".equalsIgnoreCase(args[0])) {
            CoreKVStore store = createDefaultStore();
            runDynamicDemo(store);
            return;
        }

        CoreKVStore store = createDefaultStore();
        if (args.length > 0 && "repl".equalsIgnoreCase(args[0])) {
            runRepl(store);
            return;
        }

        runGuidedDemo(store);
    }

    private static CoreKVStore createDefaultStore() throws IOException {
        return new CoreKVStore(DEFAULT_INITIAL_CAPACITY, DEFAULT_CACHE_CAPACITY, Path.of("data", "corekv.wal"));
    }

    private static void runDynamicDemo(CoreKVStore store) throws IOException {
        Random random = new Random();
        String runId = Long.toUnsignedString(Instant.now().toEpochMilli()) + "-" + Integer.toHexString(random.nextInt());
        String prefix = "demo:" + runId + ":";

        String key1 = prefix + "user:1";
        String key2 = prefix + "user:2";
        String key3 = prefix + "user:3";
        String configKey = prefix + "config:theme";

        String value1 = randomValue(random);
        String value2 = randomValue(random);
        String value3 = randomValue(random);
        String configValue = random.nextBoolean() ? "light" : "dark";

        System.out.println("CoreKV live demo starting");
        System.out.println("Run ID: " + runId);
        System.out.println("WAL file: " + store.walPath().toAbsolutePath());

        store.put(key1, value1);
        store.put(key2, value2);
        store.put(key3, value3);
        store.put(configKey, configValue);

        assertDemoCheck(value2.equals(store.get(key2)), "Random lookup should return the inserted value.");

        List<String> userKeys = store.keysWithPrefix(prefix + "user:");
        assertDemoCheck(userKeys.size() == 3, "Prefix lookup should return the three generated user keys.");

        store.delete(key3);
        assertDemoCheck(store.get(key3) == null, "Deleted key should not be retrievable.");
        assertDemoCheck(store.size() >= 3, "Store should still contain the surviving generated keys.");

        System.out.println("Generated entries:");
        System.out.println("  " + key1 + " -> " + value1);
        System.out.println("  " + key2 + " -> " + value2);
        System.out.println("  " + key3 + " -> " + value3 + " (deleted during demo)");
        System.out.println("  " + configKey + " -> " + configValue);
        System.out.println("Prefix query result for '" + prefix + "user:' -> " + userKeys);
        System.out.println("Snapshot -> " + formatSnapshot(store.snapshot()));
        System.out.println("Live demo checks passed.");
        System.out.println("Run with `java -cp out/main corekv.Main repl` for interactive mode.");
    }

    private static void runRepl(CoreKVStore store) throws IOException {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("CoreKV REPL");
            System.out.println("Commands: put <key> <value>, get <key>, delete <key>, prefix <prefix>, size, show, clear, exit");

            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) {
                    return;
                }

                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                if ("exit".equalsIgnoreCase(line)) {
                    return;
                }

                String[] parts = line.split("\\s+", 3);
                String command = parts[0].toLowerCase();

                switch (command) {
                    case "put" -> {
                        requireArguments(parts, 3);
                        store.put(parts[1], parts[2]);
                        System.out.println("OK");
                    }
                    case "get" -> {
                        requireArguments(parts, 2);
                        System.out.println(store.get(parts[1]));
                    }
                    case "delete" -> {
                        requireArguments(parts, 2);
                        System.out.println(store.delete(parts[1]));
                    }
                    case "prefix" -> {
                        requireArguments(parts, 2);
                        List<String> keys = store.keysWithPrefix(parts[1]);
                        System.out.println(keys);
                    }
                    case "size" -> System.out.println(store.size());
                    case "show" -> System.out.println(formatSnapshot(store.snapshot()));
                    case "clear" -> {
                        store.clear();
                        System.out.println("Store cleared.");
                    }
                    default -> System.out.println("Unknown command.");
                }
            }
        }
    }

    private static void runSimulationCheck() throws IOException {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("CoreKV simulation check");
            System.out.println("This mode bulk-generates unique keys, stores them, and writes a CSV verification sheet.");
            System.out.print("How many unique key-value pairs should be generated? ");

            long count = readPositiveLong(scanner);
            if (count >= 10_000_000L) {
                System.out.println("Warning: counts this large can take significant RAM, disk, and time on a normal laptop.");
                System.out.print("Type YES to continue: ");
                if (!"YES".equals(readNonBlankLine(scanner))) {
                    System.out.println("Simulation cancelled.");
                    return;
                }
            }

            String runId = Long.toUnsignedString(Instant.now().toEpochMilli());
            Path walPath = Path.of("data", "simulation-check.wal");
            Path sheetPath = Path.of("data", "simulation-check-" + runId + ".csv");
            CoreKVStore store = new CoreKVStore(DEFAULT_INITIAL_CAPACITY, Math.max(DEFAULT_CACHE_CAPACITY, 1_024), walPath);
            store.clear();

            long startedAt = System.currentTimeMillis();
            generateSimulationDataset(store, sheetPath, count);
            long durationMs = System.currentTimeMillis() - startedAt;

            System.out.println("Simulation load complete.");
            System.out.println("Generated row count: " + count);
            System.out.println("Store size: " + store.size());
            System.out.println("Sheet path: " + sheetPath.toAbsolutePath());
            System.out.println("WAL path: " + store.walPath().toAbsolutePath());
            System.out.println("Load time: " + durationMs + " ms");
            System.out.println("Commands: get <key>, prefix <prefix>, verify <key> <expectedValue>, size, sheet, sample <count>, clear, exit");

            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) {
                    return;
                }

                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                if ("exit".equalsIgnoreCase(line)) {
                    return;
                }

                String[] parts = line.split("\\s+", 3);
                String command = parts[0].toLowerCase();

                switch (command) {
                    case "get" -> {
                        requireArguments(parts, 2);
                        System.out.println(store.get(parts[1]));
                    }
                    case "prefix" -> {
                        requireArguments(parts, 2);
                        System.out.println(store.keysWithPrefix(parts[1]));
                    }
                    case "verify" -> {
                        requireArguments(parts, 3);
                        String actual = store.get(parts[1]);
                        boolean matched = parts[2].equals(actual);
                        System.out.println(matched ? "MATCH" : "MISMATCH expected=" + parts[2] + " actual=" + actual);
                    }
                    case "size" -> System.out.println(store.size());
                    case "sheet" -> System.out.println(sheetPath.toAbsolutePath());
                    case "sample" -> {
                        requireArguments(parts, 2);
                        printSheetSample(sheetPath, Integer.parseInt(parts[1]));
                    }
                    case "clear" -> {
                        store.clear();
                        System.out.println("Store cleared.");
                    }
                    default -> System.out.println("Unknown command.");
                }
            }
        }
    }

    private static void runGuidedDemo(CoreKVStore store) throws IOException {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("CoreKV guided demo");
            System.out.println("WAL file: " + store.walPath().toAbsolutePath());
            System.out.print("How many key-value pairs do you want to insert? ");

            int count = readPositiveInt(scanner);
            for (int i = 1; i <= count; i++) {
                System.out.print("Enter key " + i + ": ");
                String key = readNonBlankLine(scanner);
                System.out.print("Enter value for " + key + ": ");
                String value = scanner.nextLine();
                store.put(key, value);
                System.out.println("Stored -> " + key + "=" + value);
            }

            System.out.println("Initial load complete. You can now query the store.");
            System.out.println("Commands: get <key>, prefix <prefix>, delete <key>, put <key> <value>, size, show, clear, exit");

            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) {
                    return;
                }

                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                if ("exit".equalsIgnoreCase(line)) {
                    return;
                }

                String[] parts = line.split("\\s+", 3);
                String command = parts[0].toLowerCase();

                switch (command) {
                    case "put" -> {
                        requireArguments(parts, 3);
                        store.put(parts[1], parts[2]);
                        System.out.println("OK");
                    }
                    case "get" -> {
                        requireArguments(parts, 2);
                        System.out.println(store.get(parts[1]));
                    }
                    case "delete" -> {
                        requireArguments(parts, 2);
                        System.out.println(store.delete(parts[1]));
                    }
                    case "prefix" -> {
                        requireArguments(parts, 2);
                        System.out.println(store.keysWithPrefix(parts[1]));
                    }
                    case "size" -> System.out.println(store.size());
                    case "show" -> System.out.println(formatSnapshot(store.snapshot()));
                    case "clear" -> {
                        store.clear();
                        System.out.println("Store cleared.");
                    }
                    default -> System.out.println("Unknown command.");
                }
            }
        }
    }

    private static String formatSnapshot(List<CustomHashTable.Entry<String, String>> entries) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            CustomHashTable.Entry<String, String> entry = entries.get(i);
            builder.append(entry.key()).append("=").append(entry.value());
            if (i < entries.size() - 1) {
                builder.append(", ");
            }
        }
        return builder.append("]").toString();
    }

    private static void generateSimulationDataset(CoreKVStore store, Path sheetPath, long count) throws IOException {
        Files.createDirectories(sheetPath.getParent());
        long progressInterval = chooseProgressInterval(count);

        try (BufferedWriter writer = Files.newBufferedWriter(sheetPath, StandardCharsets.UTF_8)) {
            writer.write("sequence,prefix,key,value");
            writer.newLine();

            for (long index = 0; index < count; index++) {
                String prefix = simulationPrefix(index);
                String key = simulationKey(prefix, index);
                String value = simulationValue(index);

                store.put(key, value);
                writer.write(index + "," + csvEscape(prefix) + "," + csvEscape(key) + "," + csvEscape(value));
                writer.newLine();

                long completed = index + 1;
                if (completed % progressInterval == 0 || completed == count) {
                    System.out.println("Loaded " + completed + " / " + count);
                }
            }
        }
    }

    private static void printSheetSample(Path sheetPath, int requestedCount) throws IOException {
        int count = Math.max(1, requestedCount);
        try (Stream<String> lines = Files.lines(sheetPath, StandardCharsets.UTF_8)) {
            List<String> sample = lines.limit((long) count + 1).toList();
            for (String line : sample) {
                System.out.println(line);
            }
        }
    }

    private static void requireArguments(String[] parts, int expectedLength) {
        if (parts.length < expectedLength) {
            throw new IllegalArgumentException("Not enough arguments for command.");
        }
    }

    private static int readPositiveInt(Scanner scanner) {
        while (true) {
            String line = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(line);
                if (value <= 0) {
                    throw new NumberFormatException();
                }
                return value;
            } catch (NumberFormatException ignored) {
                System.out.print("Please enter a positive integer: ");
            }
        }
    }

    private static long readPositiveLong(Scanner scanner) {
        while (true) {
            String line = scanner.nextLine().trim();
            try {
                long value = Long.parseLong(line);
                if (value <= 0L) {
                    throw new NumberFormatException();
                }
                return value;
            } catch (NumberFormatException ignored) {
                System.out.print("Please enter a positive integer: ");
            }
        }
    }

    private static String readNonBlankLine(Scanner scanner) {
        while (true) {
            String line = scanner.nextLine().trim();
            if (!line.isEmpty()) {
                return line;
            }
            System.out.print("Input cannot be blank. Try again: ");
        }
    }

    private static String randomValue(Random random) {
        return "value-" + Integer.toUnsignedString(random.nextInt(), 36);
    }

    private static void assertDemoCheck(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Demo check failed: " + message);
        }
    }

    private static long chooseProgressInterval(long count) {
        if (count <= 10_000L) {
            return Math.max(1L, count / 10L);
        }
        return Math.max(10_000L, count / 20L);
    }

    private static String simulationPrefix(long index) {
        return plainName(index);
    }

    private static String simulationKey(String prefix, long index) {
        long bucket = index % SIMULATION_PREFIX_BUCKETS;
        return prefix + "." + leftPad(Long.toString(bucket), 4) + "." + leftPad(Long.toString(index), 12);
    }

    private static String simulationValue(long index) {
        long value = 1_000_000_000L + ((index * 48271L + 907L) % 9_000_000_000L);
        return Long.toString(value);
    }

    private static String leftPad(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        StringBuilder builder = new StringBuilder(width);
        for (int i = text.length(); i < width; i++) {
            builder.append('0');
        }
        builder.append(text);
        return builder.toString();
    }

    private static String csvEscape(String value) {
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private static String plainName(long index) {
        String firstName = FIRST_NAMES[(int) (index % FIRST_NAMES.length)];
        String lastName = LAST_NAMES[(int) ((index / FIRST_NAMES.length) % LAST_NAMES.length)];
        return firstName + "." + lastName;
    }
}
