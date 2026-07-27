package cn.superhuang.data.scalpel.business.compute.web.response;

import cn.superhuang.data.scalpel.business.compute.client.DispatcherCapabilities;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherDependency;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;

import java.util.List;

public record ComputeEngineTestResponse(
        int protocolVersion,
        String dispatcherInstanceId,
        ComputeBackendType backendType,
        String dispatcherVersion,
        DispatcherCapabilities capabilities,
        List<DispatcherDependency> dependencies
) {
    public ComputeEngineTestResponse {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
