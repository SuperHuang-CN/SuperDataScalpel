package cn.superhuang.data.scalpel.business.filedataset.web.response;

import java.time.Instant;

public record FileDatasetParseQueueSummaryResponse(
        boolean queueEnabled,
        int configuredWorkerConcurrency,
        int historyRetentionDays,
        long queuedCount,
        long runnableQueuedCount,
        long retryWaitingCount,
        long runningCount,
        long succeededCount,
        long failedCount,
        long cancelledCount,
        Instant oldestQueuedAt,
        Instant oldestRunningAt,
        Instant generatedAt
) {
}
