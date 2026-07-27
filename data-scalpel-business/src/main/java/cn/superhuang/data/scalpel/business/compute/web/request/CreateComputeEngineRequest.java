package cn.superhuang.data.scalpel.business.compute.web.request;

import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateComputeEngineRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 500) String dispatcherBaseUrl,
        @NotBlank @Size(max = 1000) String accessToken,
        @NotNull ComputeBackendType expectedBackendType,
        @NotBlank @Size(max = 249) String commandTopic,
        @NotBlank @Size(max = 249) String runnerEventTopic,
        @NotBlank @Size(max = 249) String adminEventTopic,
        @Min(0) int maxQueuedExecutions,
        @Min(1) int maxConcurrentSubmissions,
        @Min(0) int maxInFlightApplications
) {
}
