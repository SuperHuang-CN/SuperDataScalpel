package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/** Explicit planar layer-family output; not an Esri precision/tolerance compatibility mode. */
@JsonClassDescription("叠加结果几何策略：FAMILY_2D 要求可确定的点/线/面家族，将输入强制为 XY，过滤低于结果家族的接触片段并输出 MultiPoint/MultiLineString/MultiPolygon；LEGACY_GEOMETRY 输出通用 Geometry 并保留旧三模式的跨家族兼容行为。两种策略都不提供 ArcGIS 容差、吸附或自动几何修复。")
public enum SpatialOverlayGeometryPolicy {
    FAMILY_2D,
    LEGACY_GEOMETRY
}
