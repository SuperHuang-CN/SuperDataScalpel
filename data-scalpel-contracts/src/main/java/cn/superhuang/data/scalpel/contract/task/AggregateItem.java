package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("分组聚合结果中的一个指标字段；聚合只读取原始来源表，不读取本节点其他聚合项的输出。除 COUNT(*) 外，聚合函数忽略来源字段中的 SQL NULL；具体结果类型和可空性由 Spark Analyzer 决定。")
public record AggregateItem(
        @JsonPropertyDescription("必填聚合函数：COUNT、SUM、AVG、MIN 或 MAX；字段类型能否用于该函数由 Spark Analyzer 校验。")
        AggregateFunction function,
        @JsonPropertyDescription("参与聚合的来源字段名。只有 COUNT(*) 必须传 null；此时 function=COUNT 且 distinct=false。其他组合必须传已存在的非 Geometry 字段名，空字符串无效。")
        String sourceColumnName,
        @JsonPropertyDescription("生成的指标字段名；必须在聚合项之间唯一，并且不能与任何 groupByColumns 字段同名。")
        String outputColumnName,
        @JsonPropertyDescription("是否先对来源字段的非 NULL 值去重再聚合。仅 COUNT(column)、SUM(column) 和 AVG(column) 支持 true；COUNT(*)、MIN 和 MAX 必须为 false。")
        boolean distinct
) {
}
