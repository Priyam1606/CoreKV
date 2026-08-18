package corekv.wal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class WriteAheadLog {
    private final Path logPath;

    public WriteAheadLog(Path logPath) throws IOException {
        this.logPath = logPath;
        Path parent = logPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.notExists(logPath)) {
            Files.createFile(logPath);
        }
    }

    public synchronized void appendPut(String key, String value) throws IOException {
        appendLine("PUT\t" + encode(key) + "\t" + encode(value));
    }

    public synchronized void appendDelete(String key) throws IOException {
        appendLine("DELETE\t" + encode(key));
    }

    public synchronized List<WalRecord> replay() throws IOException {
        List<WalRecord> records = new ArrayList<>();
        for (String line : Files.readAllLines(logPath, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if ("PUT".equals(parts[0]) && parts.length == 3) {
                records.add(WalRecord.put(decode(parts[1]), decode(parts[2])));
            } else if ("DELETE".equals(parts[0]) && parts.length == 2) {
                records.add(WalRecord.delete(decode(parts[1])));
            }
        }
        return records;
    }

    public Path path() {
        return logPath;
    }

    public synchronized void clear() throws IOException {
        Files.writeString(
            logPath,
            "",
            StandardCharsets.UTF_8,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
    }

    private void appendLine(String line) throws IOException {
        Files.writeString(
            logPath,
            line + System.lineSeparator(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
