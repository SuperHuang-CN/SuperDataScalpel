package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "流式任务当前部署以及 TDengine 消费组清理状态。")

public record TaskStreamingStatusResponse(
        @Schema(description = "所属任务 UUID。")
        UUID taskId,
        @Schema(description = "按定义版本、Checkpoint 代次降序选择的最新正式部署；从未正式部署时为空，不包含 TRIAL 试运行。")
        TaskStreamingDeploymentResponse deployment,
        @Schema(description = "删除或替换 TDengine TMQ 输入后等待后台清理的消费组数量；非 TMQ 任务通常为 0。")
        long tmqConsumerGroupCleanupPendingCount,
        @Schema(description = "后台清理失败且仍保留失败状态的 TDengine TMQ 消费组数量；非 TMQ 任务通常为 0。")
        long tmqConsumerGroupCleanupFailedCount
) {
}
