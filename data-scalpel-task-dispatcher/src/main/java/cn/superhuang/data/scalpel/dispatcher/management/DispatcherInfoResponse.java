package cn.superhuang.data.scalpel.dispatcher.management;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;

import java.util.List;

public record DispatcherInfoResponse(
        String dispatcherInstanceId,
        ExecutionBackendType backendType,
        String version,
        DispatcherCapabilities capabilities,
        List<DispatcherDependency> dependencies
) {
}
