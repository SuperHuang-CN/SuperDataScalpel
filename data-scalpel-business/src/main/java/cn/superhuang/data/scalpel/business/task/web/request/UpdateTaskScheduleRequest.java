package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.business.task.domain.TaskMisfirePolicy;
import cn.superhuang.data.scalpel.business.task.domain.TaskOverlapPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTaskScheduleRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 120) String cronExpression,
        @Size(max = 64) String zoneId,
        TaskMisfirePolicy misfirePolicy,
        TaskOverlapPolicy overlapPolicy
) {
}
