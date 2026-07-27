package cn.superhuang.data.scalpel.dispatcher.backend;

import java.util.UUID;

public record ExecutionIdentity(UUID engineId, UUID executionId, UUID runId, int attempt) {
    public ExecutionIdentity {
        if (engineId == null || executionId == null || runId == null || attempt < 1) {
            throw new IllegalArgumentException("执行身份无效");
        }
    }
}
