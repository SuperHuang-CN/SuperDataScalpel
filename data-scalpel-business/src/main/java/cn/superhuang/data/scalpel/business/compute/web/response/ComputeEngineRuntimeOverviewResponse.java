package cn.superhuang.data.scalpel.business.compute.web.response;

import cn.superhuang.data.scalpel.contract.execution.DispatcherAdmissionCapacity;
import cn.superhuang.data.scalpel.contract.execution.DispatcherAdmissionUsage;
import cn.superhuang.data.scalpel.contract.execution.DispatcherResourceConfiguration;
import cn.superhuang.data.scalpel.contract.execution.DispatcherRuntimeDependency;
import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Safe operational snapshot of the Dispatcher currently configured for one compute engine. */
public record ComputeEngineRuntimeOverviewResponse(
        UUID engineId,
        String dispatcherInstanceId,
        ExecutionBackendType backendType,
        String version,
        String dispatcherRegistrationState,
        List<DispatcherRuntimeDependency> dependencies,
        DispatcherAdmissionCapacity admissionCapacity,
        DispatcherAdmissionUsage admissionUsage,
        DispatcherResourceConfiguration resourceConfiguration,
        Instant collectedAt
) {
    public ComputeEngineRuntimeOverviewResponse {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
