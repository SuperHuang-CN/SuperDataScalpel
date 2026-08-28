package cn.superhuang.data.scalpel.business.compute.web.response;

import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngine;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineHealthState;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineRegistrationState;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;

import java.time.Instant;
import java.util.UUID;

public record ComputeEngineResponse(
        UUID id,
        String name,
        String description,
        String dispatcherBaseUrl,
        boolean accessTokenConfigured,
        ComputeBackendType expectedBackendType,
        ComputeBackendType reportedBackendType,
        ComputeEngineRegistrationState registrationState,
        ComputeEngineHealthState healthState,
        String commandTopic,
        String runnerEventTopic,
        String adminEventTopic,
        int maxQueuedExecutions,
        int maxConcurrentSubmissions,
        int maxInFlightApplications,
        SparkExecutionResourcePolicy resourcePolicy,
        String dispatcherInstanceId,
        Instant lastCheckAt,
        String lastError,
        Instant detachedAt,
        String detachReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static ComputeEngineResponse from(ComputeEngine engine, SparkExecutionResourcePolicy resourcePolicy) {
        return new ComputeEngineResponse(
                engine.getId(), engine.getName(), engine.getDescription(), engine.getDispatcherBaseUrl(),
                true, engine.getExpectedBackendType(), engine.getReportedBackendType(),
                engine.getRegistrationState(), engine.getHealthState(), engine.getCommandTopic(),
                engine.getRunnerEventTopic(), engine.getAdminEventTopic(), engine.getMaxQueuedExecutions(),
                engine.getMaxConcurrentSubmissions(), engine.getMaxInFlightApplications(),
                resourcePolicy,
                engine.getDispatcherInstanceId(), engine.getLastCheckAt(), engine.getLastError(),
                engine.getDetachedAt(), engine.getDetachReason(),
                engine.getCreatedAt(), engine.getUpdatedAt()
        );
    }
}
