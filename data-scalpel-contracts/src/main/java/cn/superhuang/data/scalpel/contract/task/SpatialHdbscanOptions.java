package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** Explicit diagnostic output names; absent on older definitions and inactive on DBSCAN. */
@JsonClassDescription("HDBSCAN 必填的四项诊断输出字段配置；所有名称都必须非空，并与来源字段、簇/噪声字段及其他诊断字段按大小写不敏感规则保持唯一。")
public record SpatialHdbscanOptions(
        @JsonPropertyDescription("必填的 DOUBLE 成员强度字段名，取值 0 至 1；表示观测在所属选中簇中的成员强度，不是分类准确率，噪声为 0。")
        String probabilityColumnName,
        @JsonPropertyDescription("必填的 DOUBLE GLOSH 离群分数字段名，取值 0 至 1且越大越离群；不能简单解释为 1-probability。噪声也保留算法离群分数，不足最少要素时为 0。")
        String outlierColumnName,
        @JsonPropertyDescription("必填的 BOOLEAN 代表观测字段名；一个簇可以有多个代表点，不等同于唯一中心点，噪声为 false。")
        String exemplarColumnName,
        @JsonPropertyDescription("必填的可空 DOUBLE 平台归一化簇稳定性字段名；同一簇成员共享该值，噪声为 NULL。该值不声明与其他 HDBSCAN 或 ArcGIS 实现采用相同归一化公式。")
        String stabilityColumnName
) { }
