package cn.superhuang.data.scalpel.business.service.domain;

import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用于空间样式能力判断的几何族：POINT 包含点和多点；LINE 包含线和多线；POLYGON 包含面和多面；GENERIC 表示通用 Geometry 或 GeometryCollection。")
public enum SpatialGeometryFamily {
    POINT,
    LINE,
    POLYGON,
    GENERIC;

    public static SpatialGeometryFamily from(GeometryKind kind) {
        return switch (kind) {
            case POINT, MULTIPOINT -> POINT;
            case LINESTRING, MULTILINESTRING -> LINE;
            case POLYGON, MULTIPOLYGON -> POLYGON;
            case GEOMETRY, GEOMETRYCOLLECTION -> GENERIC;
        };
    }
}
