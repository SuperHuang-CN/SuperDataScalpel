package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;

/** Immutable, attempt-scoped snapshot used while a Spark JAR trial run is still executing. */
public record SparkJarTrialPreviewSnapshot(
        int schemaVersion,
        UUID executionId,
        UUID runId,
        int attempt,
        long revision,
        Instant capturedAt,
        SparkJarTrialPreview preview
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public SparkJarTrialPreviewSnapshot {
        if (schemaVersion != CURRENT_SCHEMA_VERSION || executionId == null || runId == null
                || attempt < 1 || revision < 1 || capturedAt == null || preview == null) {
            throw new IllegalArgumentException("Spark JAR 试运行预览快照无效");
        }
    }
}
