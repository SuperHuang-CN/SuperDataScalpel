package cn.superhuang.data.scalpel.filegdb.s3;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
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
    private final S3FileGdbLocation location;

    AwsS3ObjectAccess(S3Client client, S3FileGdbLocation location) {
        this.client = client;
        this.location = location;
    }

    @Override
    public S3ObjectMetadata head(String objectKey, String fileName, boolean allowMissing) {
        try {
            HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                    .bucket(location.bucket())
                    .key(objectKey)
                    .build());
            Long contentLength = response.contentLength();
            if (contentLength == null || contentLength < 0) {
                throw failure(
                        FileGdbErrorCode.IO_ERROR,
                        "S3 returned invalid metadata for FileGDB file " + fileName,
                        null);
            }
            String versionId = normalizedVersionId(response.versionId());
            String eTag = normalized(response.eTag());
            if (versionId == null && eTag == null) {
                throw failure(
                        FileGdbErrorCode.IO_ERROR,
                        "S3 metadata has no VersionId or ETag for FileGDB file " + fileName,
                        null);
            }
            return new S3ObjectMetadata(objectKey, contentLength, eTag, versionId);
        } catch (S3Exception exception) {
            if (isStatus(exception, 404) && allowMissing) {
                return null;
            }
            throw mapServiceFailure("inspect", fileName, exception);
        } catch (SdkClientException exception) {
            throw failure(
                    FileGdbErrorCode.IO_ERROR,
                    "Cannot inspect " + location.safeLocation() + " FileGDB file " + fileName,
                    exception);
        }
    }

    @Override
    public S3RangeResponse readRange(
            S3ObjectMetadata metadata,
            String fileName,
            long startInclusive,
            long endInclusive) {
        String range = "bytes=" + startInclusive + "-" + endInclusive;
        GetObjectRequest.Builder builder = GetObjectRequest.builder()
                .bucket(location.bucket())
                .key(metadata.objectKey())
                .range(range);
        if (metadata.versionId() != null) {
            builder.versionId(metadata.versionId());
        } else {
            builder.ifMatch(metadata.eTag());
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
            throw mapServiceFailure("read", fileName, exception);
        } catch (SdkClientException exception) {
            throw failure(
                    FileGdbErrorCode.IO_ERROR,
                    "Cannot read " + location.safeLocation() + " FileGDB file " + fileName,
                    exception);
        }
    }

    private FileGdbException mapServiceFailure(String operation, String fileName, S3Exception exception) {
        FileGdbErrorCode code;
        if (isStatus(exception, 404) || hasErrorCode(exception, "NoSuchKey")) {
            code = FileGdbErrorCode.MISSING_FILE;
        } else if (isStatus(exception, 412) || hasErrorCode(exception, "PreconditionFailed")) {
            code = FileGdbErrorCode.SOURCE_CHANGED;
        } else if (isStatus(exception, 416) || hasErrorCode(exception, "InvalidRange")) {
            code = FileGdbErrorCode.TRUNCATED_INPUT;
        } else {
            code = FileGdbErrorCode.IO_ERROR;
        }
        return failure(
                code,
                "Cannot " + operation + " " + location.safeLocation() + " FileGDB file " + fileName,
                exception);
    }

    private FileGdbException failure(FileGdbErrorCode code, String message, Throwable cause) {
        return cause == null ? new FileGdbException(code, message) : new FileGdbException(code, message, cause);
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
