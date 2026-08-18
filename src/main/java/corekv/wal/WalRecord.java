package corekv.wal;

public class WalRecord {
    public enum Operation {
        PUT,
        DELETE
    }

    private final Operation operation;
    private final String key;
    private final String value;

    private WalRecord(Operation operation, String key, String value) {
        this.operation = operation;
        this.key = key;
        this.value = value;
    }

    public static WalRecord put(String key, String value) {
        return new WalRecord(Operation.PUT, key, value);
    }

    public static WalRecord delete(String key) {
        return new WalRecord(Operation.DELETE, key, null);
    }

    public Operation operation() {
        return operation;
    }

    public String key() {
        return key;
    }

    public String value() {
        return value;
    }
}
