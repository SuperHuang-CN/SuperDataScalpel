package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("启动邻近传播追踪的一个实体；实体 ID 区分大小写，未给出开始时间时从 Unix Epoch 开始。")
public record TraceProximityEntityOfInterest(
        @JsonPropertyDescription("与来源表实体 ID 字段匹配的字符串值；仅用于执行，节点卡片、摘要和日志不得展示。")
        String entityId,
        @JsonPropertyDescription("可选的 Unix Epoch 毫秒开始时间；null 表示 1970-01-01T00:00:00Z。")
        Long startEpochMillis
) {
}
