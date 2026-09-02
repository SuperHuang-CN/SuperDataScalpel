package cn.superhuang.data.scalpel.contract.service;

import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Immutable PostGIS table snapshot used to publish one GeoServer feature layer. */
public record SpatialServiceDefinition(
        @Min(1) int protocolVersion,
        String catalog,
        @NotBlank String schema,
        @NotBlank String table,
        @NotBlank String geometryColumn,
        @NotNull GeometryKind geometryKind,
        @Min(1) int epsg,
        @NotBlank String primaryKeyColumn,
        @NotBlank String publishedName,
        @NotBlank String title
) {
}
