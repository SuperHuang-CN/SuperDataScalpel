package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.TaskMisfirePolicy;
import cn.superhuang.data.scalpel.business.task.domain.TaskOverlapPolicy;
import cn.superhuang.data.scalpel.business.task.domain.TaskSchedule;
import cn.superhuang.data.scalpel.business.task.domain.TaskScheduleStatus;

import java.time.Instant;
import java.util.UUID;

public record TaskScheduleResponse(
        UUID id,
        UUID taskId,
        String name,
        String cronExpression,
        String zoneId,
        TaskScheduleStatus status,
        TaskMisfirePolicy misfirePolicy,
        TaskOverlapPolicy overlapPolicy,
        Instant nextFireAt,
        Instant createdAt,
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
