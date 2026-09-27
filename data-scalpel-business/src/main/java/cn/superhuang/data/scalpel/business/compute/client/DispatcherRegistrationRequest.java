package cn.superhuang.data.scalpel.business.compute.client;

import java.util.UUID;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;

public record DispatcherRegistrationRequest(
        UUID engineId,
        DispatcherTopics topics,
        DispatcherAdmissionPolicy admissionPolicy,
        SparkExecutionResourcePolicy resourcePolicy,
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
