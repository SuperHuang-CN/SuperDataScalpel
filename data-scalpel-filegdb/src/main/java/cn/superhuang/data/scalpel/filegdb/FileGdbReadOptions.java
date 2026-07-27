package cn.superhuang.data.scalpel.filegdb;

/** Options for one bounded, forward-only feature cursor. */
public record FileGdbReadOptions(int limit) {
    public FileGdbReadOptions {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    public static FileGdbReadOptions limit(int limit) {
        return new FileGdbReadOptions(limit);
    }
}
