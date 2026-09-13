package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.UUID;

public record StreamingQueryDescriptor(
        @JsonPropertyDescription("Spark Structured Streaming 查询 UUID。")
        UUID queryId,
        @JsonPropertyDescription("运行 Manifest 中该实时输出的稳定逻辑名称。")
        String logicalName,
        @JsonPropertyDescription("实时查询写入的 Sink 类型。")
        StreamingQuerySinkType sinkType,
        @JsonPropertyDescription("该查询实际使用的 Checkpoint 对象 Key。")
        String checkpointKey
) {
    public StreamingQueryDescriptor {
        if (queryId == null || sinkType == null) {
            throw new IllegalArgumentException("实时查询描述无效");
        }
        logicalName = ExecutionContractValidation.required(logicalName, 100, "实时查询名称");
        checkpointKey = ExecutionContractValidation.required(checkpointKey, 500, "实时查询 Checkpoint Key");
    }
}
