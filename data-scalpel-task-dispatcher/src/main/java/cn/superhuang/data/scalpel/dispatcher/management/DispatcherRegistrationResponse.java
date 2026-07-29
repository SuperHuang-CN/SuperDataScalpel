package cn.superhuang.data.scalpel.dispatcher.management;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistrationState;

import java.util.UUID;

public record DispatcherRegistrationResponse(
        UUID engineId,
        String dispatcherInstanceId,
        ExecutionBackendType backendType,
        DispatcherRegistrationState state,
        DispatcherTopics topics,
        DispatcherAdmissionPolicy effectiveAdmissionPolicy,
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
                ), registration.getLastError()
        );
    }
}
