package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("只把一个字段中的 SQL NULL 替换为类型化常量；非 NULL 原值保持不变。GEOMETRY 不支持固定值填充，流任务也不能填充事件时间字段。")
public record FillNullLiteralRule(
        @JsonPropertyDescription("要填充的来源字段名；同一 NullHandlingOperation 中每个字段最多配置一次 FILL_LITERAL。流式来源的事件时间字段禁止填充。")
        String columnName,
        @JsonPropertyDescription("写入 SQL NULL 位置的非 NULL 类型化常量；dataType 必须与字段平台类型完全一致且 value 可解析。GEOMETRY 字段不支持。")
        CanvasLiteral value
) implements NullHandlingRule {
}
