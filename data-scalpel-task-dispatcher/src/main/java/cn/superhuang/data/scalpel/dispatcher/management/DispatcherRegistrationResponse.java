package cn.superhuang.data.scalpel.dispatcher.management;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistrationState;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;

import java.util.UUID;

public record DispatcherRegistrationResponse(
        UUID engineId,
        String dispatcherInstanceId,
        ExecutionBackendType backendType,
        DispatcherRegistrationState state,
        DispatcherTopics topics,
        DispatcherAdmissionPolicy effectiveAdmissionPolicy,
        SparkExecutionResourcePolicy resourcePolicy,
        String lastError
) {
    public static DispatcherRegistrationResponse from(DispatcherRegistration registration) {
        return new DispatcherRegistrationResponse(
                registration.getEngineId(),
                registration.getDispatcherInstanceId().toString(), registration.getBackendType(),
                registration.getState(),
                new DispatcherTopics(
                        registration.getCommandTopic(), registration.getRunnerEventTopic(),
                        registration.getAdminEventTopic(), registration.getRunnerControlTopic()),
                new DispatcherAdmissionPolicy(
                        registration.getMaxQueuedExecutions(), registration.getMaxConcurrentSubmissions(),
                        registration.getMaxInFlightApplications()
                ), registration.getResourcePolicy(), registration.getLastError()
        );
    }
}
