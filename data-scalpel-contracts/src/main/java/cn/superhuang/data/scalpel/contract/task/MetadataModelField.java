package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.UUID;

/** Stable model-field identity carried beside the logical Canvas schema. */
@JsonClassDescription("模型 Schema 中一个字段的稳定身份；用于把 Canvas 列名解析到字段 UUID 和排序位置。")
public record MetadataModelField(
        @JsonPropertyDescription("纳管模型字段 UUID。")
        UUID id,
        @JsonPropertyDescription("模型字段稳定编码，Canvas 通过该值引用字段。")
        String code,
        @JsonPropertyDescription("模型字段显示名称。")
        String name,
        @JsonPropertyDescription("字段在模型 Schema 中的零基排序值。")
        int sortOrder
) {
}
