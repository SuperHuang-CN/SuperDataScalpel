package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("旧版相邻驻留的聚合 Geometry。CENTROID 返回成员集合质心 Point；CONVEX_HULL 返回通用 Geometry，成员不足或共线时可退化为 Point/LineString。REFERENCE_CENTER 使用 TrackDwellResultMode。")
public enum DwellGeometryKind {
    CENTROID,
    CONVEX_HULL
}
