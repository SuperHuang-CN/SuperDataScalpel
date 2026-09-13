package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.contract.execution.CanvasTrialPreview;

import java.util.UUID;

@Schema(description = "Canvas 节点试运行的终态预览。活动运行只返回状态且 preview 为空；终态时从受控 result 制品校验运行身份后解析预览。试运行不会保存定义、写入正式输出或发布血缘。")

public record CanvasTrialPreviewResponse(
        @Schema(description = "任务运行 UUID。")
        UUID runId,
        @Schema(description = "当前试运行状态。QUEUED、RUNNING、CANCEL_REQUESTED 或 STOP_REQUESTED 时不会读取 result 制品；终态也可能因失败、取消或没有样例而无预览。")
        TaskRunStatus status,
        @Schema(description = "从兼容的终态 result 制品解析出的目标表 Schema、JSON 对象字符串行和告警；活动运行、未生成结果或结果内没有预览时为空。")
        CanvasTrialPreview preview
) {
}
