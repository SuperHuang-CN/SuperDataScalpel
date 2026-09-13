package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.business.task.domain.TaskMisfirePolicy;
import cn.superhuang.data.scalpel.business.task.domain.TaskOverlapPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "为非流式任务创建一条初始为 DISABLED 的 Quartz Cron 计划；任务发布后仍需显式启用，创建本身不会立即运行任务。")
public record CreateTaskScheduleRequest(
        @Schema(description = "计划显示名称，去除首尾空白后最长 100 个字符；同一任务内必须唯一。") @NotBlank @Size(max = 100) String name,
        @Schema(description = "Spring/Quartz 六字段 Cron 表达式，包含秒字段，去除首尾空白后最长 120 个字符。") @NotBlank @Size(max = 120) String cronExpression,
        @Schema(description = "解释 Cron 的 IANA 时区 ID，例如 Asia/Shanghai；为空或全空白时固定使用 Asia/Shanghai。") @Size(max = 64) String zoneId,
        @Schema(description = "错过计划触发点后的策略；为空默认 FIRE_ONCE_NOW，仅补触发一次。") TaskMisfirePolicy misfirePolicy,
        @Schema(description = "同一任务上一实例仍活动时的策略；FORBID 创建 SKIPPED 运行记录，ALLOW 提交独立并发运行；为空默认 FORBID。") TaskOverlapPolicy overlapPolicy
) {
}
