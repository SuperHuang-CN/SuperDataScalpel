package cn.superhuang.data.scalpel.filegdb.s3;

import cn.superhuang.data.scalpel.filegdb.FileGdbSource;
import java.util.Objects;
import software.amazon.awssdk.services.s3.S3Client;

/** Creates S3-backed sources for the core FileGDB reader. */
public final class S3FileGdbSource {
    private S3FileGdbSource() {
    }

    /**
     * Creates a source owned by {@code FileGeodatabase} after it is opened.
     * Closing the source never closes the caller-owned {@code S3Client}.
     */
    public static FileGdbSource create(
            S3Client client,
            S3FileGdbLocation location,
            S3FileGdbOptions options) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(options, "options");
        return create(new AwsS3ObjectAccess(client, location), location, options);
    }

    static FileGdbSource create(
            S3ObjectAccess objectAccess,
            S3FileGdbLocation location,
            S3FileGdbOptions options) {
        return new RangeCachingS3FileGdbSource(
                Objects.requireNonNull(objectAccess, "objectAccess"),
                Objects.requireNonNull(location, "location"),
                Objects.requireNonNull(options, "options"));
    }
}
