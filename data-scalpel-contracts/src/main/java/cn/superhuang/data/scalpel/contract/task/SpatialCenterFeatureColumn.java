package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("ANALYSIS_TABLES 模式下 CENTRAL_FEATURE 从最终选中的同一条原始记录复制的一个字段；不会用分组聚合拼接不同记录。数组只控制原字段，结果 Geometry 始终最后输出。")
public record SpatialCenterFeatureColumn(
        @JsonPropertyDescription("included=true 时必填的来源表字段名，必须存在，且同一 CENTRAL_FEATURE 投影中按大小写不敏感规则不能重复。可选择原 Geometry、分组、ID、事件时间或其他字段。")
        String sourceColumnName,
        @JsonPropertyDescription("included=true 时必填的结果字段名，按大小写不敏感规则不能与其他启用投影或 analysis.outputColumnName 重名。字段值、类型、长度、精度、nullable、注释及 Geometry 元数据来自被选中的原记录；自增/生成标记清除。")
        String outputColumnName,
        @JsonPropertyDescription("是否把该来源字段复制到中央要素结果表。false 时该项只保留编辑草稿，不校验来源和输出名，也不产生输出列；显式数组不会自动补回分组或 featureId。")
        @com.fasterxml.jackson.annotation.JsonProperty(required = true) boolean included
) { }
