package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("一个分组内的 Geometry 聚合项。聚合忽略 NULL；组内全部值为 NULL 时结果为 NULL。Empty 的行为由具体 kind 决定。结果固定声明为 nullable 通用 GEOMETRY，并独立继承来源字段的 CRS 和维度；真实拓扑错误会使任务失败，不跳过错误行。")
public record SpatialAggregation(
        @JsonPropertyDescription("必填聚合方法。UNION 融合覆盖范围；INTERSECTION 取全部输入的公共部分；COLLECT 只收集而不做拓扑融合；ENVELOPE 计算全部非空 Geometry 的总轴对齐包络。")
        SpatialAggregationKind kind,
        @JsonPropertyDescription("来源 Geometry 字段名，必须具有 EPSG CRS 和 XY 维度。不同聚合项可引用不同字段和不同 CRS，系统不会在项之间转换或统一 CRS。")
        String geometryColumnName,
        @JsonPropertyDescription("聚合结果字段名，不能与任何分组字段或其他空间聚合输出重名。结果为 nullable GEOMETRY；聚合可能升维、降维或产生 Empty，因此 GeometryKind 固定声明为 GEOMETRY。")
        String outputColumnName
) {
}
