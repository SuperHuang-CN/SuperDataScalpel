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
        boolean crsConstrained,
        SpatialStorageEncoding storageEncoding,
        SpatialMetadataStrength metadataStrength,
        String issue
) {

    public SpatialColumnMetadata(
            String nativeGeometryKind,
            Integer spatialReferenceId,
            String crsAuthority,
            Integer crsCode,
            CoordinateDimension coordinateDimension,
            boolean subtypeConstrained,
            boolean crsConstrained
    ) {
        this(
                nativeGeometryKind,
                spatialReferenceId,
                crsAuthority,
                crsCode,
                coordinateDimension,
                subtypeConstrained,
                crsConstrained,
                SpatialStorageEncoding.NATIVE,
                subtypeConstrained && crsConstrained && coordinateDimension != null
                        ? SpatialMetadataStrength.ENFORCED
                        : SpatialMetadataStrength.NONE,
                null
        );
    }

    public SpatialColumnMetadata {
        nativeGeometryKind = normalize(nativeGeometryKind);
        crsAuthority = normalize(crsAuthority);
        storageEncoding = storageEncoding == null ? SpatialStorageEncoding.NATIVE : storageEncoding;
        metadataStrength = metadataStrength == null ? SpatialMetadataStrength.NONE : metadataStrength;
        issue = issue == null || issue.isBlank() ? null : issue.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
