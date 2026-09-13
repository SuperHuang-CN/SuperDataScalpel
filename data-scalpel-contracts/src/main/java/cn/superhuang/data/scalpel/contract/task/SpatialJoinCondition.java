package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("空间 INNER Join 的一个有方向 Geometry 谓词。左右字段必须具有完整 EPSG CRS、均为 XY 且 CRS 和维度完全一致；节点不执行 CRS 转换。条件返回 NULL 时不会满足 INNER Join；Empty 与边界接触的结果遵循所选 Sedona 拓扑谓词。")
public record SpatialJoinCondition(
        @JsonPropertyDescription("leftTableName 中的 Geometry 字段名，作为空间谓词第一个参数；必须与右字段 CRS、维度一致。")
        String leftGeometryColumnName,
        @JsonPropertyDescription("必填空间拓扑谓词。它具有左右方向：CONTAINS/COVERS 从左判断右，WITHIN/COVERED_BY 从左判断是否位于右；其余取对应 Sedona 拓扑关系。")
        SpatialPredicate predicate,
        @JsonPropertyDescription("rightTableName 中的 Geometry 字段名，作为空间谓词第二个参数；必须与左字段 CRS、维度一致。")
        String rightGeometryColumnName
) {
}
