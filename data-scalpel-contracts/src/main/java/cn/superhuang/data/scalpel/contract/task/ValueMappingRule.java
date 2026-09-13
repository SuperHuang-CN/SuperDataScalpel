package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("一个非 Geometry 字段的静态精确值映射规则；显式源值按字段平台类型比较，SQL NULL 始终保持 NULL，不参与映射、不应用未匹配策略，也不触发 ERROR。流任务不能映射事件时间字段。")
public record ValueMappingRule(
        @JsonPropertyDescription("映射并原位替换结果的来源字段名；在一项操作中必须唯一。GEOMETRY 不支持，流式来源的事件时间字段也禁止映射。")
        String columnName,
        @JsonPropertyDescription("精确源值到目标值的映射数组，1 至 200 项；sourceValue 按平台类型规范化后不得重复，targetValue 对象为 NULL 明确表示输出 SQL NULL。")
        List<ValueMappingEntry> entries,
        @JsonPropertyDescription("非 NULL 原值没有命中任何 entries 时的必填策略：KEEP 保留、SET_NULL 置 NULL、SET_LITERAL 写 unmatchedValue、ERROR 在真实执行命中时失败。")
        ValueMappingUnmatchedStrategy unmatchedStrategy,
        @JsonPropertyDescription("仅 SET_LITERAL 必填的非 NULL 类型化常量，dataType 必须与字段类型一致；其他策略必须为 NULL。来源本身为 SQL NULL 时不会使用该值。")
        CanvasLiteral unmatchedValue
) {
    public ValueMappingRule {
        entries = entries == null ? null : List.copyOf(entries);
    }
}
