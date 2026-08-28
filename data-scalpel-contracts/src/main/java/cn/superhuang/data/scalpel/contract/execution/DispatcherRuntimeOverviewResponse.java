package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Current read-only operational snapshot of one Dispatcher instance. */
public record DispatcherRuntimeOverviewResponse(
        UUID engineId,
        String dispatcherInstanceId,
        ExecutionBackendType backendType,
        String version,
        String registrationState,
        List<DispatcherRuntimeDependency> dependencies,
        DispatcherAdmissionCapacity admissionCapacity,
        DispatcherAdmissionUsage admissionUsage,
        DispatcherResourceConfiguration resourceConfiguration,
        Instant collectedAt
) {
    public DispatcherRuntimeOverviewResponse {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
