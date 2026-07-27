package cn.superhuang.data.scalpel.shapefile.s3;

import cn.superhuang.data.scalpel.shapefile.ShapefileComponent;
import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

final class AwsS3ObjectAccess implements S3ObjectAccess {
    private final S3Client client;
    private final S3ShapefileLocation location;

    AwsS3ObjectAccess(S3Client client, S3ShapefileLocation location) {
        this.client = client;
        this.location = location;
    }

    @Override
    public S3ObjectSnapshot head(
            ShapefileComponent component,
            String objectKey,
            boolean allowMissing) {
        try {
            HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                    .bucket(location.bucket())
                    .key(objectKey)
                    .build());
            Long contentLength = response.contentLength();
            if (contentLength == null || contentLength < 0) {
                throw failure(
                        ShapefileErrorCode.IO_ERROR,
                        "S3 returned invalid metadata for Shapefile " + component + " component",
                        null);
            }
            String versionId = normalizedVersionId(response.versionId());
            String eTag = normalized(response.eTag());
            if (versionId == null && eTag == null) {
                throw failure(
                        ShapefileErrorCode.INVALID_SOURCE,
                        "S3 metadata has no VersionId or ETag for Shapefile " + component + " component",
                        null);
            }
            return new S3ObjectSnapshot(objectKey, contentLength, eTag, versionId);
        } catch (S3Exception exception) {
            if ((isStatus(exception, 404) || hasErrorCode(exception, "NoSuchKey")) && allowMissing) {
                return null;
            }
            throw mapServiceFailure("inspect", component, false, exception);
        } catch (SdkClientException exception) {
            throw failure(
                    ShapefileErrorCode.IO_ERROR,
                    "Cannot inspect " + location.safeLocation() + " " + component + " component",
                    exception);
        }
    }

    @Override
    public S3RangeResponse readRange(
            ShapefileComponent component,
            S3ObjectSnapshot snapshot,
            long startInclusive,
            long endInclusive) {
        String range = "bytes=" + startInclusive + "-" + endInclusive;
        GetObjectRequest.Builder builder = GetObjectRequest.builder()
                .bucket(location.bucket())
                .key(snapshot.objectKey())
                .range(range);
        if (snapshot.versionId() != null) {
            builder.versionId(snapshot.versionId());
        } else {
            builder.ifMatch(snapshot.eTag());
        }
        try {
            ResponseBytes<GetObjectResponse> responseBytes = client.getObjectAsBytes(builder.build());
            GetObjectResponse response = responseBytes.response();
            return new S3RangeResponse(
                    responseBytes.asByteArray(),
                    response.contentLength(),
                    response.contentRange(),
                    normalized(response.eTag()),
                    normalizedVersionId(response.versionId()));
        } catch (S3Exception exception) {
            throw mapServiceFailure("read", component, true, exception);
        } catch (SdkClientException exception) {
            throw failure(
                    ShapefileErrorCode.IO_ERROR,
                    "Cannot read " + location.safeLocation() + " " + component + " component",
                    exception);
        }
    }

    private ShapefileException mapServiceFailure(
            String operation,
            ShapefileComponent component,
            boolean snapshotRead,
            S3Exception exception) {
        ShapefileErrorCode code;
        if (isStatus(exception, 404) || hasErrorCode(exception, "NoSuchKey")) {
            code = snapshotRead
                    ? ShapefileErrorCode.SOURCE_CHANGED
                    : ShapefileErrorCode.MISSING_COMPONENT;
        } else if (isStatus(exception, 403) || hasErrorCode(exception, "AccessDenied")) {
            code = ShapefileErrorCode.INVALID_SOURCE;
        } else if (isStatus(exception, 412)
                || isStatus(exception, 416)
                || hasErrorCode(exception, "PreconditionFailed")
                || hasErrorCode(exception, "InvalidRange")) {
            code = ShapefileErrorCode.SOURCE_CHANGED;
        } else {
            code = ShapefileErrorCode.IO_ERROR;
        }
        return failure(
                code,
                "Cannot " + operation + " " + location.safeLocation() + " " + component + " component",
                exception);
    }

    private static ShapefileException failure(
            ShapefileErrorCode code,
            String message,
            Throwable cause) {
        return cause == null
                ? new ShapefileException(code, message)
                : new ShapefileException(code, message, cause);
    }

    private static boolean isStatus(S3Exception exception, int statusCode) {
        return exception.statusCode() == statusCode;
    }

    private static boolean hasErrorCode(S3Exception exception, String expected) {
        return exception.awsErrorDetails() != null
                && expected.equals(exception.awsErrorDetails().errorCode());
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalizedVersionId(String value) {
        String normalized = normalized(value);
        return "null".equals(normalized) ? null : normalized;
    }
}
