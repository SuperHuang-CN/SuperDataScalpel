package cn.superhuang.data.scalpel.business.task.web.response;

import java.time.Instant;
import java.util.UUID;

public record TaskRunLogResponse(
        UUID runId,
        Status status,
        Source source,
        String content,
        Instant collectedAt,
        Integer windowSizeBytes,
        boolean truncated,
        String message,
        Long finalSizeBytes,
        boolean finalPreviewAvailable,
        boolean finalDownloadAvailable
) {
    public enum Status {
        WAITING,
        LIVE,
        ARCHIVING,
        FINAL,
        UNAVAILABLE
    }

    public enum Source {
        NONE,
        DISPATCHER,
        ARTIFACT
    }
}
