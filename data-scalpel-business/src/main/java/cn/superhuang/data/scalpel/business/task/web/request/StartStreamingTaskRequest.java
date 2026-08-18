package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.contract.execution.StreamingCheckpointMode;
import jakarta.validation.constraints.NotNull;

public record StartStreamingTaskRequest(@NotNull StreamingCheckpointMode checkpointMode) {
}
