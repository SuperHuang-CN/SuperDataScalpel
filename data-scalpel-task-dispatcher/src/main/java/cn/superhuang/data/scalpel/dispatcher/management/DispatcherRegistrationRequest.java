package cn.superhuang.data.scalpel.dispatcher.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;

public record DispatcherRegistrationRequest(
        @NotNull UUID engineId,
        @NotNull @Valid DispatcherTopics topics,
        @NotNull @Valid DispatcherAdmissionPolicy admissionPolicy,
        @NotNull @Valid SparkExecutionResourcePolicy resourcePolicy,
        String targetKey,
        String dispatcherInstanceId,
        cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType expectedBackendType,
        String targetFingerprint
) {
    public DispatcherRegistrationRequest(UUID engineId, DispatcherTopics topics,
            DispatcherAdmissionPolicy admissionPolicy, SparkExecutionResourcePolicy resourcePolicy) {
        this(engineId, topics, admissionPolicy, resourcePolicy, null, null, null, null);
    }
}
