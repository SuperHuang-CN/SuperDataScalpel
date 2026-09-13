package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;

@JsonClassDescription("用户 Spark JAR 作业自行上报的业务阶段与状态说明；独立于平台执行状态和失败阶段。")
public record UserJobStatus(
        @JsonPropertyDescription("用户作业自行上报的当前业务阶段标识，例如 RUNNING 或 WRITE_OUTPUT；不等同于平台失败阶段。")
        String phase,
        @JsonPropertyDescription("用户作业对当前 phase 的安全状态说明。")
        String message,
        @JsonPropertyDescription("用户作业生成本次状态的时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public UserJobStatus {
        phase = ExecutionContractValidation.required(phase, 100, "用户作业阶段");
        message = ExecutionContractValidation.required(message, 1000, "用户作业状态说明");
        if (!phase.matches("[A-Za-z][A-Za-z0-9._-]{0,99}") || updatedAt == null) {
            throw new IllegalArgumentException("用户作业状态无效");
        }
    }
}
