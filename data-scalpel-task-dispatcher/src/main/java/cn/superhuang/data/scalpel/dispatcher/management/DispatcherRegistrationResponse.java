package cn.superhuang.data.scalpel.dispatcher.management;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistrationState;

import java.util.UUID;

public record DispatcherRegistrationResponse(
        int protocolVersion,
        UUID engineId,
        String dispatcherInstanceId,
        ExecutionBackendType backendType,
        long configRevision,
        DispatcherRegistrationState state,
        DispatcherTopics topics,
        DispatcherAdmissionPolicy effectiveAdmissionPolicy,
        String lastError
) {
    public static DispatcherRegistrationResponse from(DispatcherRegistration registration) {
        return new DispatcherRegistrationResponse(
                registration.getProtocolVersion(), registration.getEngineId(),
                registration.getDispatcherInstanceId().toString(), registration.getBackendType(),
                registration.getConfigRevision(), registration.getState(),
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
