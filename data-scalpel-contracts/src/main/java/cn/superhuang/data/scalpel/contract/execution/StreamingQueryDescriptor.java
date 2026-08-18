package cn.superhuang.data.scalpel.contract.execution;

import java.util.UUID;

public record StreamingQueryDescriptor(
        UUID queryId,
        String logicalName,
        StreamingQuerySinkType sinkType,
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
