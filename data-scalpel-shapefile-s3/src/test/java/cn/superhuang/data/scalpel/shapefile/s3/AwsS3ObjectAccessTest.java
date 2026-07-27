package cn.superhuang.data.scalpel.shapefile.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.shapefile.ShapefileComponent;
import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
    private static final S3ShapefileLocation LOCATION =
            S3ShapefileLocation.fromShpKey("bucket", "datasets/roads.shp");

    @Test
    void pinsVersionIdAndValidatesResponseMetadata() {
        CapturingClient handler = new CapturingClient();
        handler.headResponse = HeadObjectResponse.builder()
                .contentLength(3L)
                .eTag("\"etag-v1\"")
                .versionId("version-1")
                .build();
        handler.getBytes = bytes(new byte[] {1, 2, 3}, "bytes 0-2/3", "\"etag-v1\"", "version-1");
        AwsS3ObjectAccess access = new AwsS3ObjectAccess(handler.client(), LOCATION);

        S3ObjectSnapshot snapshot = access.head(
                ShapefileComponent.SHP,
                "datasets/roads.shp",
                false);
        S3RangeResponse response = access.readRange(ShapefileComponent.SHP, snapshot, 0, 2);

        assertEquals("bucket", handler.headRequest.bucket());
        assertEquals("datasets/roads.shp", handler.headRequest.key());
        assertEquals("bytes=0-2", handler.getRequest.range());
        assertEquals("version-1", handler.getRequest.versionId());
        assertNull(handler.getRequest.ifMatch());
        assertEquals(3, response.bytes().length);
    }

    @Test
    void usesIfMatchWithoutVersionAndNeverClosesCallerClient() {
        CapturingClient handler = readableClient();
        S3Client client = handler.client();
        AwsS3ObjectAccess access = new AwsS3ObjectAccess(client, LOCATION);
        S3ObjectSnapshot snapshot = access.head(
                ShapefileComponent.SHP,
                "datasets/roads.shp",
                false);

        access.readRange(ShapefileComponent.SHP, snapshot, 0, 0);
        assertEquals("\"etag\"", handler.getRequest.ifMatch());
        assertNull(handler.getRequest.versionId());

        try (var source = S3ShapefileSource.create(
                client, LOCATION, new S3ShapefileOptions(64 * 1024, 64 * 1024))) {
            assertEquals("s3", client.serviceName());
        }
        assertEquals(0, handler.closeCalls);

        handler.headFailure = serviceFailure(503, "SlowDown");
        assertCode(
                ShapefileErrorCode.IO_ERROR,
                () -> S3ShapefileSource.create(
                        client, LOCATION, new S3ShapefileOptions(64 * 1024, 64 * 1024)));
        assertEquals(0, handler.closeCalls);
    }

    @Test
    void mapsServiceAndTransportFailuresWithoutLeakingDetails() {
        CapturingClient optionalMissing = new CapturingClient();
        optionalMissing.headFailure = serviceFailure(404, "NoSuchKey");
        assertNull(new AwsS3ObjectAccess(optionalMissing.client(), LOCATION).head(
                ShapefileComponent.CPG, "datasets/roads.cpg", true));

        CapturingClient optionalForbidden = new CapturingClient();
        optionalForbidden.headFailure = serviceFailure(403, "AccessDenied");
        assertCode(
                ShapefileErrorCode.INVALID_SOURCE,
                () -> new AwsS3ObjectAccess(optionalForbidden.client(), LOCATION).head(
                        ShapefileComponent.CPG, "datasets/roads.cpg", true));

        assertHeadFailure(404, "NoSuchKey", ShapefileErrorCode.MISSING_COMPONENT);
        assertHeadFailure(403, "AccessDenied", ShapefileErrorCode.INVALID_SOURCE);
        assertGetFailure(404, "NoSuchKey", ShapefileErrorCode.SOURCE_CHANGED);
        assertGetFailure(403, "AccessDenied", ShapefileErrorCode.INVALID_SOURCE);
        assertGetFailure(412, "PreconditionFailed", ShapefileErrorCode.SOURCE_CHANGED);
        assertGetFailure(416, "InvalidRange", ShapefileErrorCode.SOURCE_CHANGED);
        assertGetFailure(503, "SlowDown", ShapefileErrorCode.IO_ERROR);

        CapturingClient transport = readableClient();
        transport.getFailure = SdkClientException.create("secret transport details");
        ShapefileException exception = assertCode(
                ShapefileErrorCode.IO_ERROR,
                () -> readOne(transport));
        assertTrue(!exception.getMessage().contains("secret transport details"));
    }

    @Test
    void requiresVersionIdOrEtag() {
        CapturingClient handler = new CapturingClient();
        handler.headResponse = HeadObjectResponse.builder().contentLength(1L).build();
        assertCode(
                ShapefileErrorCode.INVALID_SOURCE,
                () -> new AwsS3ObjectAccess(handler.client(), LOCATION).head(
                        ShapefileComponent.SHP, "datasets/roads.shp", false));
    }

    private static void assertHeadFailure(int status, String awsCode, ShapefileErrorCode expected) {
        CapturingClient handler = new CapturingClient();
        handler.headFailure = serviceFailure(status, awsCode);
        assertCode(expected, () -> new AwsS3ObjectAccess(handler.client(), LOCATION).head(
                ShapefileComponent.SHP, "datasets/roads.shp", false));
    }

    private static void assertGetFailure(int status, String awsCode, ShapefileErrorCode expected) {
        CapturingClient handler = readableClient();
        handler.getFailure = serviceFailure(status, awsCode);
        assertCode(expected, () -> readOne(handler));
    }

    private static void readOne(CapturingClient handler) {
        AwsS3ObjectAccess access = new AwsS3ObjectAccess(handler.client(), LOCATION);
        S3ObjectSnapshot snapshot = access.head(
                ShapefileComponent.SHP,
                "datasets/roads.shp",
                false);
        access.readRange(ShapefileComponent.SHP, snapshot, 0, 0);
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

    private static ShapefileException assertCode(ShapefileErrorCode expected, Runnable action) {
        ShapefileException exception = assertThrows(ShapefileException.class, action::run);
        assertEquals(expected, exception.errorCode());
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
