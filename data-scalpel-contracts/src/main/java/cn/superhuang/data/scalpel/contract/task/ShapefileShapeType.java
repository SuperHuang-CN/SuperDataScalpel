package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Shapefile 目标 Shape 类型：POINT、MULTIPOINT、POLYLINE 或 POLYGON。Point 可提升为单元素 MultiPoint，LineString/Polygon 可规范化为对应 Multi 类型；不兼容或 Empty Geometry 不能写出。")
public enum ShapefileShapeType {
    POINT,
    MULTIPOINT,
    POLYLINE,
    POLYGON
}
