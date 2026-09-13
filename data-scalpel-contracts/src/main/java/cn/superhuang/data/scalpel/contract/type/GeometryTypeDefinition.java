package cn.superhuang.data.scalpel.contract.type;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** Geometry subtype together with its stable CRS and coordinate dimension. */
@JsonClassDescription("平台 GEOMETRY 逻辑类型的空间结构参数；同时固定几何子类型、坐标参考系和坐标维度。")
public record GeometryTypeDefinition(
        @JsonPropertyDescription("几何子类型，例如 POINT、LINESTRING、POLYGON 或对应多几何类型。")
        GeometryKind kind,
        @JsonPropertyDescription("稳定的坐标参考系机构与编码，不使用数据库本地 SRID 代替。")
        CrsReference crs,
        @JsonPropertyDescription("坐标维度；当前受管与交换契约使用 XY。")
        CoordinateDimension dimension
) {

    public GeometryTypeDefinition {
        if (kind == null) {
            throw new IllegalArgumentException("Geometry kind is required");
        }
        if (crs == null) {
            throw new IllegalArgumentException("Geometry CRS is required");
        }
        if (dimension == null) {
            throw new IllegalArgumentException("Geometry coordinate dimension is required");
        }
    }
}
