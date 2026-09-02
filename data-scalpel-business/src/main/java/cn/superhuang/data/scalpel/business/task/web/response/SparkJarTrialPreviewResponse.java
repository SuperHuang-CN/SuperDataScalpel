package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.contract.execution.SparkJarTrialPreview;

import java.util.UUID;

public record SparkJarTrialPreviewResponse(
        UUID runId,
        TaskRunStatus status,
        SparkJarTrialPreview preview
) {
}
