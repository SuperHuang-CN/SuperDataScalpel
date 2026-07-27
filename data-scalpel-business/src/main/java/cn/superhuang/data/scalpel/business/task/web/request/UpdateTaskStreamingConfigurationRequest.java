package cn.superhuang.data.scalpel.business.task.web.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateTaskStreamingConfigurationRequest(
        @Min(1) @Max(300) int triggerIntervalSeconds
) {
}
