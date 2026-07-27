package cn.superhuang.data.scalpel.admin.filedataset;

import cn.superhuang.data.scalpel.business.filedataset.storage.S3FileObjectStorage;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadOptions;
import cn.superhuang.data.scalpel.filegdb.FileGeodatabase;
import cn.superhuang.data.scalpel.filegdb.testutil.TestFileGdbBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3FileObjectStorageGdbTests {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void opensARealGdbBelowTheConfiguredRootAndDeletesOnlyItsPrefix() throws Exception {
        TestFileGdbBuilder.Fixture fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        String root = "private-root";
        String prefix = "file-datasets/materialized/sample.gdb";
        Map<String, byte[]> objects = new LinkedHashMap<>();
        try (var files = Files.list(fixture.directory())) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                objects.put(root + "/" + prefix + "/" + file.getFileName(), Files.readAllBytes(file));
            }
        }
        objects.put(root + "/file-datasets/raw/sample.zip", new byte[] {1, 2, 3});
        objects.put(root + "/file-datasets/materialized/other.gdb/gdb", new byte[] {0, 0, 0, 0});

        InMemoryS3 handler = new InMemoryS3(objects);
        S3FileObjectStorage storage = new S3FileObjectStorage(handler.client(), "private-bucket", root);
        try (FileGeodatabase database = storage.openFileGeodatabase(prefix)) {
            assertEquals(
                    List.of("ScalarPointXY", "PointXYZ", "PointXYM", "PointXYZM"),
                    database.layers().stream().map(layer -> layer.name()).toList()
            );
            try (var cursor = database.openCursor(
                    fixture.layerId("ScalarPointXY"), FileGdbReadOptions.limit(1)
            )) {
                assertTrue(cursor.hasNext());
                assertEquals("中文 café", cursor.next().attribute("Name"));
            }
        }

        storage.deletePrefix(prefix);

        assertEquals(root + "/" + prefix + "/", handler.lastListPrefix);
        assertFalse(handler.objects.keySet().stream().anyMatch(key -> key.startsWith(root + "/" + prefix + "/")));
        assertTrue(handler.objects.containsKey(root + "/file-datasets/raw/sample.zip"));
        assertTrue(handler.objects.containsKey(root + "/file-datasets/materialized/other.gdb/gdb"));
    }

    private static final class InMemoryS3 implements InvocationHandler {
        private final Map<String, byte[]> objects;
        private String lastListPrefix;

        private InMemoryS3(Map<String, byte[]> objects) {
            this.objects = new LinkedHashMap<>(objects);
        }

        private S3Client client() {
            return (S3Client) Proxy.newProxyInstance(
                    S3Client.class.getClassLoader(), new Class<?>[] {S3Client.class}, this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "headObject" -> head((HeadObjectRequest) arguments[0]);
                case "getObjectAsBytes" -> get((GetObjectRequest) arguments[0]);
                case "listObjectsV2" -> list((ListObjectsV2Request) arguments[0]);
                case "deleteObjects" -> delete((DeleteObjectsRequest) arguments[0]);
                case "serviceName" -> "s3";
                case "close" -> null;
                case "toString" -> "InMemoryS3Client";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException("Unexpected S3Client method " + method);
            };
        }

        private HeadObjectResponse head(HeadObjectRequest request) {
            byte[] bytes = requireObject(request.key());
            return HeadObjectResponse.builder()
                    .contentLength((long) bytes.length)
                    .eTag(etag(request.key()))
                    .build();
        }

        private ResponseBytes<GetObjectResponse> get(GetObjectRequest request) {
            byte[] source = requireObject(request.key());
            String[] range = request.range().substring("bytes=".length()).split("-", 2);
            int start = Integer.parseInt(range[0]);
            int end = Integer.parseInt(range[1]);
            byte[] bytes = Arrays.copyOfRange(source, start, end + 1);
            GetObjectResponse response = GetObjectResponse.builder()
                    .contentLength((long) bytes.length)
                    .contentRange("bytes " + start + "-" + end + "/" + source.length)
                    .eTag(etag(request.key()))
                    .build();
            return ResponseBytes.fromByteArray(response, bytes);
        }

        private ListObjectsV2Response list(ListObjectsV2Request request) {
            lastListPrefix = request.prefix();
            List<S3Object> contents = objects.keySet().stream()
                    .filter(key -> key.startsWith(request.prefix()))
                    .map(key -> S3Object.builder().key(key).size((long) objects.get(key).length).build())
                    .toList();
            return ListObjectsV2Response.builder().contents(contents).isTruncated(false).build();
        }

        private DeleteObjectsResponse delete(DeleteObjectsRequest request) {
            request.delete().objects().forEach(object -> objects.remove(object.key()));
            return DeleteObjectsResponse.builder().build();
        }

        private byte[] requireObject(String key) {
            byte[] bytes = objects.get(key);
            if (bytes == null) {
                throw (S3Exception) S3Exception.builder()
                        .statusCode(404)
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchKey").build())
                        .build();
            }
            return bytes;
        }

        private static String etag(String key) {
            return "\"etag-" + Integer.toHexString(key.hashCode()) + "\"";
        }
    }
}
