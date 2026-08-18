package cn.superhuang.datascalpel.sdk;

/** affectedRows is null when the target cannot provide a reliable metric. */
public record WriteResult(Long affectedRows) {
    public static WriteResult known(long affectedRows) {
        if (affectedRows < 0) {
            throw new IllegalArgumentException("affectedRows must not be negative");
        }
        return new WriteResult(affectedRows);
    }

    public static WriteResult unknown() {
        return new WriteResult(null);
    }
}
