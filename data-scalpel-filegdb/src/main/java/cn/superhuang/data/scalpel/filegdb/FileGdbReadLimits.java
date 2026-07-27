package cn.superhuang.data.scalpel.filegdb;

/** Resource limits applied before allocating memory or iterating untrusted counts. */
public record FileGdbReadLimits(
        long maxTableFileBytes,
        int maxFields,
        int maxRecordBytes,
        int maxStringBytes,
        int maxBinaryBytes,
        int maxGeometryParts,
        int maxGeometryPoints,
        int maxIndexSlotsPerCursor,
        int maxFeaturesPerCursor) {

    public static final long DEFAULT_MAX_TABLE_FILE_BYTES = 16L * 1024 * 1024 * 1024;
    public static final int DEFAULT_MAX_FIELDS = 1_024;
    public static final int DEFAULT_MAX_RECORD_BYTES = 64 * 1024 * 1024;
    public static final int DEFAULT_MAX_STRING_BYTES = 16 * 1024 * 1024;
    public static final int DEFAULT_MAX_BINARY_BYTES = 64 * 1024 * 1024;
    public static final int DEFAULT_MAX_GEOMETRY_PARTS = 100_000;
    public static final int DEFAULT_MAX_GEOMETRY_POINTS = 10_000_000;
    public static final int DEFAULT_MAX_INDEX_SLOTS_PER_CURSOR = 100_000_000;
    public static final int DEFAULT_MAX_FEATURES_PER_CURSOR = 100_000;

    public FileGdbReadLimits {
        requirePositive(maxTableFileBytes, "maxTableFileBytes");
        requirePositive(maxFields, "maxFields");
        requirePositive(maxRecordBytes, "maxRecordBytes");
        requirePositive(maxStringBytes, "maxStringBytes");
        requirePositive(maxBinaryBytes, "maxBinaryBytes");
        requirePositive(maxGeometryParts, "maxGeometryParts");
        requirePositive(maxGeometryPoints, "maxGeometryPoints");
        requirePositive(maxIndexSlotsPerCursor, "maxIndexSlotsPerCursor");
        requirePositive(maxFeaturesPerCursor, "maxFeaturesPerCursor");
    }

    public static FileGdbReadLimits defaults() {
        return new FileGdbReadLimits(
                DEFAULT_MAX_TABLE_FILE_BYTES,
                DEFAULT_MAX_FIELDS,
                DEFAULT_MAX_RECORD_BYTES,
                DEFAULT_MAX_STRING_BYTES,
                DEFAULT_MAX_BINARY_BYTES,
                DEFAULT_MAX_GEOMETRY_PARTS,
                DEFAULT_MAX_GEOMETRY_POINTS,
                DEFAULT_MAX_INDEX_SLOTS_PER_CURSOR,
                DEFAULT_MAX_FEATURES_PER_CURSOR);
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
