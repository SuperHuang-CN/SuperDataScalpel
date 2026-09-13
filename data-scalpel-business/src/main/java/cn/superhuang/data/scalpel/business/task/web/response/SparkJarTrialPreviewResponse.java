package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.contract.execution.SparkJarTrialPreview;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "批处理或流式 Spark JAR 在线源码试运行的最新可用写入预览。活动运行优先读取尝试级快照；终态优先读取 result 制品，均会校验执行 UUID、运行 UUID 和尝试序号。")

public record SparkJarTrialPreviewResponse(
        @Schema(description = "任务运行 UUID。")
        UUID runId,
        @Schema(description = "当前试运行状态。预览来源与技术状态独立：活动状态可能已有快照，SUCCESS 也可能没有受控写入预览，失败或停止终态可能保留已形成的最终预览。")
        TaskRunStatus status,
        @Schema(description = "预览来源：NONE 尚无可读预览，RUNNING_SNAPSHOT 为活动尝试写入的最新快照，FINAL_RESULT 为终态 result 制品中的结果。")
        PreviewSource source,
        @Schema(description = "当前执行尝试内从 1 开始单调递增的快照修订号；只在 source=RUNNING_SNAPSHOT 时非空，不能跨尝试比较。")
        Long revision,
        @Schema(description = "RUNNING_SNAPSHOT 的快照形成时间，或 FINAL_RESULT 中可解析的运行结束时间；NONE 或终态结果缺少合法 endedAt 时为空。")
        Instant capturedAt,
        @Schema(description = "是否已读取并验证终态 result 制品；source=FINAL_RESULT 时为 true，即使该制品中的 trialPreview 为空。")
        boolean finalResult,
        @Schema(description = "当前可用的受控写入目标、Schema 与有限 JSON 行；尚未形成预览或作业没有调用受控写入时为空。")
        SparkJarTrialPreview preview
) {
    @Schema(description = "试运行预览读取来源：NONE 无可读内容；RUNNING_SNAPSHOT 活动尝试快照；FINAL_RESULT 已验证的终态 result 制品。")
    public enum PreviewSource {
        NONE,
        RUNNING_SNAPSHOT,
        FINAL_RESULT
    }
}
