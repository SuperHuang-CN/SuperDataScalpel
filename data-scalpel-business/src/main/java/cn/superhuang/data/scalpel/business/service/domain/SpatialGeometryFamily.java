package cn.superhuang.data.scalpel.business.service.domain;

import cn.superhuang.data.scalpel.contract.type.GeometryKind;

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
