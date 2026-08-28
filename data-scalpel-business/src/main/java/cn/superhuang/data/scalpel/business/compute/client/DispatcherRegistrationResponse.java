package cn.superhuang.data.scalpel.business.compute.client;

import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;

import java.util.UUID;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;

public record DispatcherRegistrationResponse(
        UUID engineId,
        String dispatcherInstanceId,
        ComputeBackendType backendType,
        DispatcherRegistrationState state,
        DispatcherTopics topics,
        DispatcherAdmissionPolicy effectiveAdmissionPolicy,
        SparkExecutionResourcePolicy resourcePolicy,
        String lastError
) {
}
