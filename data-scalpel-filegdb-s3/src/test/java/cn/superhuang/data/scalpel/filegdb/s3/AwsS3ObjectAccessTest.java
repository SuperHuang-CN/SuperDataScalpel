package cn.superhuang.data.scalpel.filegdb.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.filegdb.FileGdbRandomAccessObject;
import cn.superhuang.data.scalpel.filegdb.FileGdbSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class AwsS3ObjectAccessTest {
    private static final S3FileGdbLocation LOCATION =
            new S3FileGdbLocation("bucket", "datasets/sample.gdb");

    @Test
    void pinsVersionIdAndNeverClosesCallerClient() {
        CapturingClient handler = new CapturingClient();
        handler.headResponse = HeadObjectResponse.builder()
                .contentLength(3L)
                .eTag("\"etag-v1\"")
                .versionId("version-1")
                .build();
        handler.getBytes = bytes(
                new byte[] {1, 2, 3},
                "bytes 0-2/3",
                "\"etag-v1\"",
                "version-1");
        S3Client client = handler.client();
        FileGdbSource source = S3FileGdbSource.create(client, LOCATION, S3FileGdbOptions.defaults());

        try (FileGdbRandomAccessObject object = source.open("gdb")) {
            ByteBuffer target = ByteBuffer.allocate(3);
            assertEquals(3, object.read(0, target));
            assertEquals(List.of((byte) 1, (byte) 2, (byte) 3),
                    List.of(target.array()[0], target.array()[1], target.array()[2]));
        }
        source.close();

        assertEquals("bucket", handler.headRequest.bucket());
        assertEquals("datasets/sample.gdb/gdb", handler.headRequest.key());
        assertEquals("bytes=0-2", handler.getRequest.range());
        assertEquals("version-1", handler.getRequest.versionId());
        assertNull(handler.getRequest.ifMatch());
        assertEquals(0, handler.closeCalls);
        assertEquals("s3", client.serviceName(), "the caller-owned client remains usable");
    }

    @Test
    void usesIfMatchWhenVersionIdIsUnavailable() {
        CapturingClient handler = new CapturingClient();
        handler.headResponse = HeadObjectResponse.builder()
                .contentLength(2L)
                .eTag("\"etag-only\"")
                .build();
        handler.getBytes = bytes(new byte[] {7, 8}, "bytes 0-1/2", "\"etag-only\"", null);

        try (FileGdbSource source = S3FileGdbSource.create(
                        handler.client(), LOCATION, S3FileGdbOptions.defaults());
                FileGdbRandomAccessObject object = source.open("a00000001.gdbtable")) {
            assertEquals(2, object.read(0, ByteBuffer.allocate(2)));
        }

        assertEquals("\"etag-only\"", handler.getRequest.ifMatch());
        assertNull(handler.getRequest.versionId());
        assertTrue(handler.getRequest.range().startsWith("bytes="));
    }

    @Test
    void mapsHeadAndGetFailuresToStableCodes() {
        CapturingClient missingHead = new CapturingClient();
        missingHead.headFailure = serviceFailure(404, "NoSuchKey");
        try (FileGdbSource source = S3FileGdbSource.create(
                missingHead.client(), LOCATION, S3FileGdbOptions.defaults())) {
            assertFalse(source.exists("gdb"));
        }

        CapturingClient forbiddenHead = new CapturingClient();
        forbiddenHead.headFailure = serviceFailure(403, "AccessDenied");
        assertCode(FileGdbErrorCode.IO_ERROR, () -> S3FileGdbSource.create(
                forbiddenHead.client(), LOCATION, S3FileGdbOptions.defaults()).exists("gdb"));

        assertGetFailure(404, "NoSuchKey", FileGdbErrorCode.MISSING_FILE);
        assertGetFailure(403, "AccessDenied", FileGdbErrorCode.IO_ERROR);
        assertGetFailure(412, "PreconditionFailed", FileGdbErrorCode.SOURCE_CHANGED);
        assertGetFailure(416, "InvalidRange", FileGdbErrorCode.TRUNCATED_INPUT);
        assertGetFailure(503, "SlowDown", FileGdbErrorCode.IO_ERROR);

        CapturingClient transportFailure = readableClient();
        transportFailure.getFailure = SdkClientException.create("secret transport details");
        FileGdbException exception = assertCode(
                FileGdbErrorCode.IO_ERROR,
                () -> readOne(transportFailure.client()));
        assertFalse(exception.getMessage().contains("secret transport details"));
    }

    @Test
    void rejectsVersionOrEtagChangesInSuccessfulResponses() {
        CapturingClient changedVersion = readableClient();
        changedVersion.headResponse = HeadObjectResponse.builder()
                .contentLength(1L)
                .eTag("\"etag\"")
                .versionId("v1")
                .build();
        changedVersion.getBytes = bytes(new byte[] {1}, "bytes 0-0/1", "\"etag\"", "v2");
        assertCode(FileGdbErrorCode.SOURCE_CHANGED, () -> readOne(changedVersion.client()));

        CapturingClient changedEtag = readableClient();
        changedEtag.getBytes = bytes(new byte[] {1}, "bytes 0-0/1", "\"changed\"", null);
        assertCode(FileGdbErrorCode.SOURCE_CHANGED, () -> readOne(changedEtag.client()));
    }

    private static void assertGetFailure(int status, String errorCode, FileGdbErrorCode expected) {
        CapturingClient handler = readableClient();
        handler.getFailure = serviceFailure(status, errorCode);
        assertCode(expected, () -> readOne(handler.client()));
    }

    private static void readOne(S3Client client) {
        try (FileGdbSource source = S3FileGdbSource.create(client, LOCATION, S3FileGdbOptions.defaults());
                FileGdbRandomAccessObject object = source.open("gdb")) {
            object.read(0, ByteBuffer.allocate(1));
        }
    }

    private static CapturingClient readableClient() {
        CapturingClient handler = new CapturingClient();
        handler.headResponse = HeadObjectResponse.builder()
                .contentLength(1L)
                .eTag("\"etag\"")
                .build();
        handler.getBytes = bytes(new byte[] {1}, "bytes 0-0/1", "\"etag\"", null);
        return handler;
    }

    private static ResponseBytes<GetObjectResponse> bytes(
            byte[] bytes,
            String contentRange,
            String eTag,
            String versionId) {
        GetObjectResponse response = GetObjectResponse.builder()
                .contentLength((long) bytes.length)
                .contentRange(contentRange)
                .eTag(eTag)
                .versionId(versionId)
                .build();
        return ResponseBytes.fromByteArray(response, bytes);
    }

    private static S3Exception serviceFailure(int status, String errorCode) {
        return (S3Exception) S3Exception.builder()
                .statusCode(status)
                .message("secret service response")
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode(errorCode)
                        .errorMessage("secret response body")
                        .serviceName("S3")
                        .build())
                .build();
    }

    private static FileGdbException assertCode(FileGdbErrorCode code, Runnable action) {
        FileGdbException exception = assertThrows(FileGdbException.class, action::run);
        assertEquals(code, exception.code());
        return exception;
    }

    private static final class CapturingClient implements InvocationHandler {
        private HeadObjectResponse headResponse;
        private ResponseBytes<GetObjectResponse> getBytes;
        private RuntimeException headFailure;
        private RuntimeException getFailure;
        private HeadObjectRequest headRequest;
        private GetObjectRequest getRequest;
        private int closeCalls;

        private S3Client client() {
            return (S3Client) Proxy.newProxyInstance(
                    S3Client.class.getClassLoader(),
                    new Class<?>[] {S3Client.class},
                    this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "headObject" -> head(arguments);
                case "getObjectAsBytes" -> get(arguments);
                case "serviceName" -> "s3";
                case "close" -> {
                    closeCalls++;
                    yield null;
                }
                case "toString" -> "CapturingS3Client";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException("Unexpected S3Client method " + method);
            };
        }

        private Object head(Object[] arguments) {
            headRequest = (HeadObjectRequest) arguments[0];
            if (headFailure != null) {
                throw headFailure;
            }
            return headResponse;
        }

        private Object get(Object[] arguments) {
            getRequest = (GetObjectRequest) arguments[0];
            if (getFailure != null) {
                throw getFailure;
            }
            return getBytes;
        }
    }
}
