package cn.superhuang.data.scalpel.dialect.model;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;

import java.util.Locale;

/**
 * Dialect-enriched spatial metadata for one physical column.
 *
 * <p>The numeric spatial reference identifier is database-local. Authority/code identify the
 * stable CRS only when the dialect could resolve the local identifier through the database
 * catalog.</p>
 */
public record SpatialColumnMetadata(
        String nativeGeometryKind,
        Integer spatialReferenceId,
        String crsAuthority,
        Integer crsCode,
        CoordinateDimension coordinateDimension,
        boolean subtypeConstrained,
        boolean crsConstrained
) {

    public SpatialColumnMetadata {
        nativeGeometryKind = normalize(nativeGeometryKind);
        crsAuthority = normalize(crsAuthority);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
