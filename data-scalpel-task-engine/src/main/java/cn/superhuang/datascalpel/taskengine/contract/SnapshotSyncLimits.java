package cn.superhuang.datascalpel.taskengine.contract;

public record SnapshotSyncLimits(
        int maxRowsPerSide,
        long maxEstimatedBytes,
        int lockTimeoutSeconds
) {
    public SnapshotSyncLimits {
        if (maxRowsPerSide < 1 || maxEstimatedBytes < 1 || lockTimeoutSeconds < 1) {
            throw new IllegalArgumentException("Snapshot Sync limits must be positive");
        }
    }

    public static SnapshotSyncLimits defaults() {
        return new SnapshotSyncLimits(100_000, 256L * 1024 * 1024, 30);
    }
}
