package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** One independently addressable Kafka sink in a Kafka output node. */
@JsonClassDescription("Kafka 输出节点中的一个独立 Topic Sink；valueFormat 非 NULL 时直接按 JSON/TEXT/BINARY 选择上游字段，valueFormat=NULL 时保留 Canvas 4.0–4.5 的内联 JSON Schema、显式映射和旧 Key 语义。")
public record KafkaOutputWrite(
        @JsonPropertyDescription("必填且在当前输出节点内唯一的 UUID 字符串；用于稳定标识 Sink、StreamingQuery、Checkpoint 和运行结果，修改消息内容时不应随意更换。")
        String writeId,
        @JsonPropertyDescription("必填的上游无界逻辑表名；Kafka Output 不接受有界批处理表。")
        String sourceTableName,
        @JsonPropertyDescription("必填的目标 Kafka Topic 名称。")
        String topic,
        @JsonPropertyDescription("消息 Value 编码格式；JSON/TEXT/BINARY 启用 Canvas 4.6 直接字段模式，NULL 精确表示旧版 JSON Schema/映射兼容模式，不应自动补成 JSON。")
        KafkaOutputValueFormat valueFormat,
        @JsonPropertyDescription("Canvas 4.6 模式必填且不能重复的来源字段名。JSON 至少一项，并按上游 Schema 顺序而非此数组顺序组成对象；TEXT/BINARY 必须且只能一项，类型分别为 STRING/BINARY。旧版模式必须为空列表。")
        List<String> valueColumnNames,
        @JsonPropertyDescription("可选的 Kafka Key 来源字段；NULL 或空白表示不设置 Key。Canvas 4.6 模式只允许 STRING/BINARY 并保持原类型；该字段是否也进入 JSON Value 由 valueColumnNames 独立决定。旧版模式保留既有字符串 Key 行为。")
        String keyColumnName,
        @JsonPropertyDescription("仅 valueFormat=NULL 的旧版模式使用，必须包含至少一个非 Geometry 字段并定义目标 JSON 对象；Canvas 4.6 模式必须为 NULL 或 columns=[]。不连接 Schema Registry。")
        KafkaValueSchema valueSchema,
        @JsonPropertyDescription("仅旧版模式使用的来源字段到 valueSchema 目标字段映射及显式 Cast；Canvas 4.6 模式必须为空列表。NULL 列表在构造时规范化为空列表。")
        List<JdbcColumnMapping> columnMappings
) {
    public KafkaOutputWrite {
        valueColumnNames = valueColumnNames == null ? List.of() : List.copyOf(valueColumnNames);
        columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
    }

    /** Canvas 4.0-4.5 compatibility constructor for the legacy JSON schema/mapping mode. */
    public KafkaOutputWrite(
            String writeId,
            String sourceTableName,
            String topic,
            KafkaValueSchema valueSchema,
            String keyColumnName,
            List<JdbcColumnMapping> columnMappings
    ) {
        this(
                writeId, sourceTableName, topic,
                null, List.of(), keyColumnName, valueSchema, columnMappings
        );
    }

    public boolean legacyMappingMode() {
        return valueFormat == null;
    }
}
