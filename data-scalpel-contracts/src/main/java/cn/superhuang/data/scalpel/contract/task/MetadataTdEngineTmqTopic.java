package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("供流式 Canvas 使用的 TDengine TMQ Topic 元数据快照；绑定超级表、时间精度、结构指纹和字段 Schema。")
public record MetadataTdEngineTmqTopic(
        @JsonPropertyDescription("Kafka 或 TDengine 主题名。")
        String topicName,
        @JsonPropertyDescription("数据库 Catalog；不适用时为空。")
        String catalogName,
        @JsonPropertyDescription("TDengine 超级表名。")
        String supertableName,
        @JsonPropertyDescription("当前 Topic 与超级表结构的规范化指纹，用于检测定义变化。")
        String definitionFingerprint,
        @JsonPropertyDescription("兼容旧版本定义时记录的旧摘要；没有时为空。")
        String legacyDefinitionFingerprint,
        @JsonPropertyDescription("TDengine 时间戳精度，例如 ms、us 或 ns。")
        String timePrecision,
        @JsonPropertyDescription("Topic 对应超级表的列 Schema，包含时间列、普通列和标签列的稳定顺序。")
        List<CanvasColumnSchema> columns
) {
    public MetadataTdEngineTmqTopic {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
