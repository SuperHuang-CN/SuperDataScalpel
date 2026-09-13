package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

@JsonClassDescription("Kafka JSON 内联 Schema 中的一个扁平字段；使用平台类型和明确的 STRING/DECIMAL 参数，不支持 Geometry、嵌套对象或数组。")

public record KafkaValueColumn(
        @JsonPropertyDescription("必填的消息字段名，长度 1 至 255，在同一 Schema 内精确区分且不能重复。Kafka Input 中还不能与选定元数据的固定输出名重名。")
        String name,
        @JsonPropertyDescription("必填的平台数据类型；Geometry 不受支持，复杂 JSON 对象和数组也不在该扁平契约中。")
        PlatformDataType fieldType,
        @JsonPropertyDescription("仅 STRING 可选的正整数长度；其他类型必须为 NULL。它描述逻辑 Schema，不会让 Kafka 截断消息。")
        Integer length,
        @JsonPropertyDescription("DECIMAL 必填的总有效位数，范围 1 至 38；其他类型必须为 NULL。")
        Integer precision,
        @JsonPropertyDescription("DECIMAL 必填的小数位数，范围 0 至 precision；其他类型必须为 NULL。")
        Integer scale,
        @JsonPropertyDescription("该 JSON 字段是否允许缺失或值为 NULL；不代表整条 Kafka Value tombstone 是否允许。")
        boolean nullable,
        @JsonPropertyDescription("可选的字段用途或业务含义说明，只进入逻辑 Schema，不写入消息内容。")
        String comment
) {
}
