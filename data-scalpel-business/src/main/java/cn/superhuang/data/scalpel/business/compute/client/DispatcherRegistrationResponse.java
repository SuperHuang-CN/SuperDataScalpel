package cn.superhuang.data.scalpel.business.compute.client;

import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;

import java.util.UUID;

public record DispatcherRegistrationResponse(
        UUID engineId,
        String dispatcherInstanceId,
        ComputeBackendType backendType,
        DispatcherRegistrationState state,
        DispatcherTopics topics,
        DispatcherAdmissionPolicy effectiveAdmissionPolicy,
        String lastError
) {
}
