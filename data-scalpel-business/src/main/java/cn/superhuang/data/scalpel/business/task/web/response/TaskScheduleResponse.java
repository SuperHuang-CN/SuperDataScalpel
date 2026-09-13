package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.task.domain.TaskMisfirePolicy;
import cn.superhuang.data.scalpel.business.task.domain.TaskOverlapPolicy;
import cn.superhuang.data.scalpel.business.task.domain.TaskSchedule;
import cn.superhuang.data.scalpel.business.task.domain.TaskScheduleStatus;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "任务的 Quartz Cron 调度配置及下一次预计触发时间。")

public record TaskScheduleResponse(
        @Schema(description = "任务调度 UUID，用于更新、启停和删除该 Cron 计划。")
        UUID id,
        @Schema(description = "所属任务 UUID。")
        UUID taskId,
        @Schema(description = "调度计划显示名称。")
        String name,
        @Schema(description = "Quartz Cron 表达式。")
        String cronExpression,
        @Schema(description = "解释 Cron 表达式的 IANA 时区标识。")
        String zoneId,
        @Schema(description = "计划配置状态：ENABLED 表示任务处于 PUBLISHED 时应参与 Cron 调度；所属任务停用后计划可仍保持 ENABLED，但 Quartz 被暂停且 nextFireAt 为空。DISABLED 不触发。")
        TaskScheduleStatus status,
        @Schema(description = "Quartz 错过触发时间时的策略：FIRE_ONCE_NOW 恢复后立即补触发一次，SKIP 跳过错过的触发。")
        TaskMisfirePolicy misfirePolicy,
        @Schema(description = "同一任务仍有活动运行时的策略：FORBID 保存一条 SKIPPED 运行记录但不提交执行，ALLOW 提交独立并发运行。")
        TaskOverlapPolicy overlapPolicy,
        @Schema(description = "按 cronExpression 和 zoneId 计算的下一次计划触发时间，ISO-8601 UTC 时间戳；计划停用或表达式没有后续时间时为空。")
        Instant nextFireAt,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "名称、Cron、时区、策略或启停状态最后变更时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {

    public static TaskScheduleResponse from(TaskSchedule schedule, Instant nextFireAt) {
        return new TaskScheduleResponse(
                schedule.getId(), schedule.getTaskId(), schedule.getName(), schedule.getCronExpression(),
                schedule.getZoneId(), schedule.getStatus(), schedule.getMisfirePolicy(), schedule.getOverlapPolicy(),
                nextFireAt, schedule.getCreatedAt(), schedule.getUpdatedAt()
        );
    }
}
