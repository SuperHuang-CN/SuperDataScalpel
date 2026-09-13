package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("值映射规则中的一项精确替换；源值必须是非 NULL 且与字段同平台类型的常量，目标值对象为 NULL 表示写入 SQL NULL。")
public record ValueMappingEntry(
        @JsonPropertyDescription("用于精确匹配字段原值的非 NULL 类型化常量；dataType 必须与字段一致，按类型规范化后在同一规则中唯一，例如 DECIMAL 1.0 与 1.00 视为重复。")
        CanvasLiteral sourceValue,
        @JsonPropertyDescription("命中后写入的同类型非 NULL 常量；整个 targetValue 对象为 NULL 时写入 SQL NULL。不能用 value=NULL 的 CanvasLiteral 表示空值。")
        CanvasLiteral targetValue
) {
}
