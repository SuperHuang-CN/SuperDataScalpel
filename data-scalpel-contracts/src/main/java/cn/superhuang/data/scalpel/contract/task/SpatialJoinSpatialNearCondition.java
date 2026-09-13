package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Canvas 4.60 起空间连接的一项可选空间邻近条件。它独立于拓扑谓词，并与拓扑、属性和时间条件按 AND 组合。")
public record SpatialJoinSpatialNearCondition(
        @JsonPropertyDescription("左侧目标表的 Geometry 字段名。")
        String leftGeometryColumnName,
        @JsonPropertyDescription("右侧连接表的 Geometry 字段名。")
        String rightGeometryColumnName,
        @JsonPropertyDescription("必填距离方法。PLANAR 在来源 CRS 中计算；GEODESIC 使用 EPSG:4326 XY Geometry 的真实最近位置测地距离。")
        SpatialDistanceMethod distanceMethod,
        @JsonPropertyDescription("必填的有限正距离阈值。")
        Double distance,
        @JsonPropertyDescription("必填距离单位。GEODESIC 不允许 SOURCE_CRS_UNIT。")
        SpatialDistanceUnit distanceUnit
) {
}
