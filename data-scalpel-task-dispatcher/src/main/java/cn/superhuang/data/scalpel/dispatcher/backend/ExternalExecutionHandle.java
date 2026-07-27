package cn.superhuang.data.scalpel.dispatcher.backend;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;

public record ExternalExecutionHandle(ExecutionBackendType backendType, String externalId, String trackingUrl) {
    public ExternalExecutionHandle {
        if (backendType == null || externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("外部执行 Handle 无效");
        }
    }
}
