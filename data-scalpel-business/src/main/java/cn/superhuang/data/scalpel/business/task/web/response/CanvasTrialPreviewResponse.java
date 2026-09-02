package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.contract.execution.CanvasTrialPreview;

import java.util.UUID;

public record CanvasTrialPreviewResponse(
        UUID runId,
        TaskRunStatus status,
        CanvasTrialPreview preview
) {
}
