package cn.superhuang.data.scalpel.business.compute.client;

import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;

import java.util.List;

public record DispatcherInfoResponse(
        String dispatcherInstanceId,
        ComputeBackendType backendType,
        String version,
        DispatcherCapabilities capabilities,
        List<DispatcherDependency> dependencies
) {
    public DispatcherInfoResponse {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
