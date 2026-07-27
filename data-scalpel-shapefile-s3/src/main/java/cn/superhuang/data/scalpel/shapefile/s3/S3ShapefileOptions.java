package cn.superhuang.data.scalpel.shapefile.s3;

/** Bounded block-cache settings for one open S3-backed Shapefile. */
public record S3ShapefileOptions(int blockSizeBytes, long maxCacheBytes) {
    public static final int DEFAULT_BLOCK_SIZE_BYTES = 1024 * 1024;
    public static final long DEFAULT_MAX_CACHE_BYTES = 64L * 1024 * 1024;
    public static final int MIN_BLOCK_SIZE_BYTES = 64 * 1024;
    public static final int MAX_BLOCK_SIZE_BYTES = 8 * 1024 * 1024;

    public S3ShapefileOptions {
        if (blockSizeBytes < MIN_BLOCK_SIZE_BYTES
                || blockSizeBytes > MAX_BLOCK_SIZE_BYTES
                || Integer.bitCount(blockSizeBytes) != 1) {
            throw new IllegalArgumentException(
                    "S3 block size must be a power of two between 64 KiB and 8 MiB");
        }
        if (maxCacheBytes < blockSizeBytes || maxCacheBytes % blockSizeBytes != 0) {
            throw new IllegalArgumentException(
                    "S3 cache size must contain a positive whole number of blocks");
        }
    }

    public static S3ShapefileOptions defaults() {
        return new S3ShapefileOptions(DEFAULT_BLOCK_SIZE_BYTES, DEFAULT_MAX_CACHE_BYTES);
    }
}
