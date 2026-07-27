package cn.superhuang.data.scalpel.shapefile.s3;

import cn.superhuang.data.scalpel.shapefile.ShapefileSource;
import java.util.Objects;
import software.amazon.awssdk.services.s3.S3Client;

/** Creates S3-backed sources for the core Shapefile reader. */
public final class S3ShapefileSource {
    private S3ShapefileSource() {
    }

    /**
     * Captures all configured component metadata and returns a source that is owned by
     * {@code ShapefileDataset} after it is opened. Closing the source never closes the caller-owned client.
     */
    public static ShapefileSource create(
            S3Client client,
            S3ShapefileLocation location,
            S3ShapefileOptions options) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(options, "options");
        return create(new AwsS3ObjectAccess(client, location), location, options);
    }

    static ShapefileSource create(
            S3ObjectAccess objectAccess,
            S3ShapefileLocation location,
            S3ShapefileOptions options) {
        Objects.requireNonNull(objectAccess, "objectAccess");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(options, "options");
        RangeCachingS3ShapefileSource source = null;
        try {
            source = new RangeCachingS3ShapefileSource(objectAccess, location, options);
            return source;
        } catch (RuntimeException exception) {
            if (source != null) {
                try {
                    source.close();
                } catch (RuntimeException closeFailure) {
                    exception.addSuppressed(closeFailure);
                }
            }
            throw exception;
        }
    }
}
