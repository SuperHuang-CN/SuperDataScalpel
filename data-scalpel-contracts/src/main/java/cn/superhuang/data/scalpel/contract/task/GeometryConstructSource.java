package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GeometryConstructSource.Wkt.class, name = "WKT"),
        @JsonSubTypes.Type(value = GeometryConstructSource.Wkb.class, name = "WKB"),
        @JsonSubTypes.Type(value = GeometryConstructSource.GeoJson.class, name = "GEOJSON"),
        @JsonSubTypes.Type(value = GeometryConstructSource.PointFromXy.class, name = "POINT_FROM_XY")
})
@JsonClassDescription("Geometry 构造的严格来源联合；kind 决定从 STRING WKT、BINARY WKB、STRING GeoJSON Geometry 解析，或把 X/Y 数值字段 Cast 为 DOUBLE 后创建 Point。解析后只设置 targetGeometry CRS，不做重投影。")
public sealed interface GeometryConstructSource permits
        GeometryConstructSource.Wkt,
        GeometryConstructSource.Wkb,
        GeometryConstructSource.GeoJson,
        GeometryConstructSource.PointFromXy {

    @JsonClassDescription("从 STRING 字段解析 WKT Geometry；NULL 输入产生 NULL，空白、畸形文本或解析类型与 targetGeometry.kind 不同会在实际执行时失败。")

    record Wkt(
            @JsonPropertyDescription("保存 WKT 文本的必填 STRING 来源字段名。WKT 中的坐标被视为已经属于 targetGeometry.crs。")
            String columnName
    ) implements GeometryConstructSource {
    }

    @JsonClassDescription("从 BINARY 字段解析 WKB Geometry；不接受十六进制或 Base64 STRING。NULL 输入产生 NULL，损坏字节或解析类型与 targetGeometry.kind 不同会在实际执行时失败。")

    record Wkb(
            @JsonPropertyDescription("保存原始 WKB 字节的必填 BINARY 来源字段名。解析后由节点设置 targetGeometry.crs。")
            String columnName
    ) implements GeometryConstructSource {
    }

    @JsonClassDescription("从 STRING 字段解析一个 GeoJSON Geometry 对象；不是 Feature 或 FeatureCollection。NULL 输入产生 NULL，畸形 JSON 或解析类型与 targetGeometry.kind 不同会在实际执行时失败。")

    record GeoJson(
            @JsonPropertyDescription("保存 GeoJSON Geometry JSON 文本的必填 STRING 来源字段名；坐标被视为已经属于 targetGeometry.crs。")
            String columnName
    ) implements GeometryConstructSource {
    }

    @JsonClassDescription("把两个数值字段 Cast 为 DOUBLE 并创建 Point；任一坐标为 NULL 时结果为 NULL。不会检查经纬度范围、交换轴顺序或执行坐标转换。")

    record PointFromXy(
            @JsonPropertyDescription("点 X 坐标来源字段名；必须是 BYTE、SHORT、INTEGER、LONG、FLOAT、DOUBLE 或 DECIMAL。")
            String xColumnName,
            @JsonPropertyDescription("点 Y 坐标来源字段名；必须是 BYTE、SHORT、INTEGER、LONG、FLOAT、DOUBLE 或 DECIMAL。")
            String yColumnName
    ) implements GeometryConstructSource {
    }
}
