package cn.superhuang.data.scalpel.shapefile;

/** Resource limits checked before allocations or untrusted iteration. */
public record ShapefileReadLimits(
        long maxComponentFileBytes,
        int maxFields,
        int maxRecordBytes,
        int maxMetadataBytes,
        int maxParts,
        int maxGeometryPoints,
        long maxIndexRecords,
        int maxFeaturesPerCursor) {

    public static final long DEFAULT_MAX_COMPONENT_FILE_BYTES = 4L * 1024 * 1024 * 1024;
    public static final int DEFAULT_MAX_FIELDS = 1_024;
    public static final int DEFAULT_MAX_RECORD_BYTES = 64 * 1024 * 1024;
    public static final int DEFAULT_MAX_METADATA_BYTES = 1024 * 1024;
    public static final int DEFAULT_MAX_PARTS = 100_000;
    public static final int DEFAULT_MAX_GEOMETRY_POINTS = 10_000_000;
    public static final long DEFAULT_MAX_INDEX_RECORDS = 100_000_000L;
    public static final int DEFAULT_MAX_FEATURES_PER_CURSOR = 100_000;
    public static final int ABSOLUTE_MAX_FEATURES_PER_CURSOR = Integer.MAX_VALUE;

    public ShapefileReadLimits {
        requirePositive(maxComponentFileBytes, "maxComponentFileBytes");
        requirePositive(maxFields, "maxFields");
        requirePositive(maxRecordBytes, "maxRecordBytes");
        requirePositive(maxMetadataBytes, "maxMetadataBytes");
        requirePositive(maxParts, "maxParts");
        requirePositive(maxGeometryPoints, "maxGeometryPoints");
        requirePositive(maxIndexRecords, "maxIndexRecords");
        requirePositive(maxFeaturesPerCursor, "maxFeaturesPerCursor");
        requireAtMost(maxComponentFileBytes, DEFAULT_MAX_COMPONENT_FILE_BYTES, "maxComponentFileBytes");
        requireAtMost(maxFields, DEFAULT_MAX_FIELDS, "maxFields");
        requireAtMost(maxRecordBytes, DEFAULT_MAX_RECORD_BYTES, "maxRecordBytes");
        requireAtMost(maxMetadataBytes, DEFAULT_MAX_METADATA_BYTES, "maxMetadataBytes");
        requireAtMost(maxParts, DEFAULT_MAX_PARTS, "maxParts");
        requireAtMost(maxGeometryPoints, DEFAULT_MAX_GEOMETRY_POINTS, "maxGeometryPoints");
        requireAtMost(maxIndexRecords, DEFAULT_MAX_INDEX_RECORDS, "maxIndexRecords");
        requireAtMost(maxFeaturesPerCursor, ABSOLUTE_MAX_FEATURES_PER_CURSOR, "maxFeaturesPerCursor");
    }

    public static ShapefileReadLimits defaults() {
        return new ShapefileReadLimits(
                DEFAULT_MAX_COMPONENT_FILE_BYTES,
                DEFAULT_MAX_FIELDS,
                DEFAULT_MAX_RECORD_BYTES,
                DEFAULT_MAX_METADATA_BYTES,
                DEFAULT_MAX_PARTS,
                DEFAULT_MAX_GEOMETRY_POINTS,
                DEFAULT_MAX_INDEX_RECORDS,
                DEFAULT_MAX_FEATURES_PER_CURSOR);
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireAtMost(long value, long maximum, String name) {
        if (value > maximum) {
            throw new IllegalArgumentException(name + " can only tighten the built-in safety limit");
        }
    }
}
