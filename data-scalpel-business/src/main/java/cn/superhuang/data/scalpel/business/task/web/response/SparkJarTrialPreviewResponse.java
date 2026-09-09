package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.contract.execution.SparkJarTrialPreview;

import java.time.Instant;
import java.util.UUID;

public record SparkJarTrialPreviewResponse(
        UUID runId,
        TaskRunStatus status,
        PreviewSource source,
        Long revision,
        Instant capturedAt,
        boolean finalResult,
        SparkJarTrialPreview preview
) {
    public enum PreviewSource {
        NONE,
        RUNNING_SNAPSHOT,
        FINAL_RESULT
    }
}
