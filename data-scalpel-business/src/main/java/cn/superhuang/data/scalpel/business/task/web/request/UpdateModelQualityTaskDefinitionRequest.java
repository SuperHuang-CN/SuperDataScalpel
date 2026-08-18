package cn.superhuang.data.scalpel.business.task.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

public record UpdateModelQualityTaskDefinitionRequest(
        @NotNull UUID modelId,
        @Min(0) @Max(1000) Integer failureSampleLimit
) {
}
