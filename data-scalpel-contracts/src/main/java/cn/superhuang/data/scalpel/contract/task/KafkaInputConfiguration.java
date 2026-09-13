package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("流式 Kafka 输入配置；持续订阅一个 Topic，把 Value 按 JSON、UTF-8 文本或原始二进制解码为无界逻辑表，并可按固定名称追加 Kafka 元数据字段。节点本身不配置 Broker、认证或 Schema Registry。")

public record KafkaInputConfiguration(
        @JsonPropertyDescription("必填的 Kafka 数据源 UUID 字符串；编译和运行时必须对应已启用、具有 SOURCE 用途的 Kafka 连接。Broker 和凭据从受保护的数据源配置取得。")
        String dataSourceId,
        @JsonPropertyDescription("必填的单个 Kafka Topic 名称。")
        String topic,
        @JsonPropertyDescription("Value 的内联结构；JSON 必须提供至少一个合法字段，TEXT/BINARY 必须提供 columns=[]，不能为 NULL。该 Schema 不连接 Schema Registry。")
        KafkaValueSchema valueSchema,
        @JsonPropertyDescription("当前操作产生的 Canvas 逻辑表名；必须在任务定义内唯一，后续节点通过该值引用结果。")
        String outputTableName,
        @JsonPropertyDescription("必填的首次消费位置：EARLIEST 从可用最早 Offset 开始，LATEST 从启动时末尾开始；已有 Spark Checkpoint 时由 Checkpoint Offset 接管。")
        KafkaStartingOffsets startingOffsets,
        @JsonPropertyDescription("必填的流式微批触发间隔秒数，范围 1 至 300；旧构造器默认 10 秒。实时任务只允许一个无界输入，其触发间隔成为任务触发间隔。")
        Integer triggerIntervalSeconds,
        @JsonPropertyDescription("Value 解码格式：JSON 按 valueSchema 以 FAILFAST 解析对象；TEXT 固定以 UTF-8 解码到可空 STRING 字段 value；BINARY 原样输出到可空 BINARY 字段 value。NULL 兼容旧定义并按 JSON 处理。")
        KafkaInputValueFormat valueFormat,
        @JsonPropertyDescription("可选且不能重复的元数据字段集合；NULL 按空集合处理。输出不按配置顺序，而按 KEY、TOPIC、PARTITION、OFFSET、TIMESTAMP 的固定枚举顺序追加；任一固定输出名与 Value 字段重名时编译失败。")
        List<KafkaInputMetadataField> metadataFields
) {
    public static final int DEFAULT_TRIGGER_INTERVAL_SECONDS = 10;

    public KafkaInputConfiguration {
        metadataFields = metadataFields == null ? null : List.copyOf(metadataFields);
    }

    public KafkaInputConfiguration(
            String dataSourceId,
            String topic,
            KafkaValueSchema valueSchema,
            String outputTableName,
            KafkaStartingOffsets startingOffsets,
            Integer triggerIntervalSeconds
    ) {
        this(
                dataSourceId, topic, valueSchema, outputTableName, startingOffsets,
                triggerIntervalSeconds, null, null
        );
    }

    public KafkaInputConfiguration(
            String dataSourceId,
            String topic,
            KafkaValueSchema valueSchema,
            String outputTableName,
            KafkaStartingOffsets startingOffsets
    ) {
        this(
                dataSourceId, topic, valueSchema, outputTableName, startingOffsets,
                DEFAULT_TRIGGER_INTERVAL_SECONDS, null, null
        );
    }

    public KafkaInputValueFormat effectiveValueFormat() {
        return valueFormat == null ? KafkaInputValueFormat.JSON : valueFormat;
    }

    public List<KafkaInputMetadataField> effectiveMetadataFields() {
        return metadataFields == null ? List.of() : metadataFields;
    }
}
