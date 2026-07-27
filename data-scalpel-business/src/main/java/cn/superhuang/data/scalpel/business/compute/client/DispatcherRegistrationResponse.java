package cn.superhuang.data.scalpel.business.compute.client;

import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;

import java.util.UUID;

public record DispatcherRegistrationResponse(
        int protocolVersion,
        UUID engineId,
        String dispatcherInstanceId,
        ComputeBackendType backendType,
        long configRevision,
        DispatcherRegistrationState state,
        DispatcherTopics topics,
        DispatcherAdmissionPolicy effectiveAdmissionPolicy,
        String lastError
) {
}
