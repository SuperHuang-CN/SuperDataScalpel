package cn.superhuang.data.scalpel.dialect.model;

import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;

/** One trusted model Geometry column requested for spatial preview inspection or rendering. */
public record SpatialPreviewColumn(String name, GeometryTypeDefinition geometry) {
    public SpatialPreviewColumn {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Spatial preview column name is required");
        }
        name = name.trim();
        if (geometry == null) {
            throw new IllegalArgumentException("Spatial preview Geometry definition is required");
        }
    }
}
