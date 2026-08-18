package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;

public record UserJobStatus(String phase, String message, Instant updatedAt) {
    public UserJobStatus {
        phase = ExecutionContractValidation.required(phase, 100, "用户作业阶段");
        message = ExecutionContractValidation.required(message, 1000, "用户作业状态说明");
        if (!phase.matches("[A-Za-z][A-Za-z0-9._-]{0,99}") || updatedAt == null) {
            throw new IllegalArgumentException("用户作业状态无效");
        }
    }
}
