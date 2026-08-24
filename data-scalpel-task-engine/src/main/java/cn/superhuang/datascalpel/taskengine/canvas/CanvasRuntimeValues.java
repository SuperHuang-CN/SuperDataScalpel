package cn.superhuang.datascalpel.taskengine.canvas;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Small, trusted runtime context exposed to Canvas operators.
 * It deliberately does not expose the full execution manifest.
 */
public record CanvasRuntimeValues(
        UUID executionId,
        Instant executionStartedAt,
        boolean preview
) {
    private static final UUID PREVIEW_EXECUTION_ID = new UUID(0L, 0L);

    public CanvasRuntimeValues {
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(executionStartedAt, "executionStartedAt");
    }

    public static CanvasRuntimeValues forPreview() {
        return new CanvasRuntimeValues(PREVIEW_EXECUTION_ID, Instant.EPOCH, true);
    }

    public static CanvasRuntimeValues execution(UUID executionId, Instant executionStartedAt) {
        return new CanvasRuntimeValues(executionId, executionStartedAt, false);
    }
}
