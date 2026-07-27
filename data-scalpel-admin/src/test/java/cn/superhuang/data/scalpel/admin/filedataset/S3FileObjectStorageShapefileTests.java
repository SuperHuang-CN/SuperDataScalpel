package cn.superhuang.data.scalpel.admin.filedataset;

import cn.superhuang.data.scalpel.business.filedataset.storage.S3FileObjectStorage;
import cn.superhuang.data.scalpel.shapefile.ShapefileComponent;
import cn.superhuang.data.scalpel.shapefile.ShapefileDataset;
import cn.superhuang.data.scalpel.shapefile.ShapefileOpenOptions;
import cn.superhuang.data.scalpel.shapefile.ShapefileReadOptions;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;
import cn.superhuang.data.scalpel.shapefile.testutil.TestShapefileBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3FileObjectStorageShapefileTests {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void opensCanonicalComponentsBelowTheRootWithoutProbingAbsentOptionalObjects() throws Exception {
        Path componentDirectory = temporaryDirectory.resolve("components");
        new TestShapefileBuilder(
                componentDirectory,
                "roads",
                ShapefileShapeType.POINT,
                new ShapefileEnvelope(120, 30, 121, 31, 0, 0, 0, 0)
        ).field("name", 'C', 20, 0)
                .record(TestShapefileBuilder.point(
                        ShapefileShapeType.POINT, 120.5, 30.5, null, null
                ), false, "Road A")
                .write();

        String root = "private-root";
        String prefix = "file-datasets/materialized/sample";
        Map<String, byte[]> objects = new LinkedHashMap<>();
        for (ShapefileComponent component : EnumSet.of(
                ShapefileComponent.SHP, ShapefileComponent.SHX, ShapefileComponent.DBF
        )) {
            objects.put(
                    root + "/" + prefix + "/data." + component.extension(),
                    Files.readAllBytes(componentDirectory.resolve("roads." + component.extension()))
            );
        }

        InMemoryS3 handler = new InMemoryS3(objects);
        S3FileObjectStorage storage = new S3FileObjectStorage(
                handler.client(), "private-bucket", root
        );
        try (ShapefileDataset dataset = storage.openShapefile(
                prefix,
                EnumSet.of(ShapefileComponent.SHP, ShapefileComponent.SHX, ShapefileComponent.DBF),
                ShapefileOpenOptions.defaults()
        )) {
            assertEquals(ShapefileShapeType.POINT, dataset.schema().shapeType());
            assertEquals(1, dataset.schema().recordCount());
            try (var cursor = dataset.openCursor(ShapefileReadOptions.limit(1))) {
                assertTrue(cursor.hasNext());
                assertEquals("Road A", cursor.next().attribute("name"));
            }
        }

        List<String> requiredKeys = List.of(
                root + "/" + prefix + "/data.shp",
                root + "/" + prefix + "/data.shx",
                root + "/" + prefix + "/data.dbf"
        );
        assertEquals(requiredKeys, handler.headKeys);
        assertFalse(handler.headKeys.stream().anyMatch(key -> key.endsWith(".cpg") || key.endsWith(".prj")));
        assertFalse(handler.rangeKeys.isEmpty());
        assertTrue(handler.rangeKeys.stream().allMatch(requiredKeys::contains));
    }

    private static final class InMemoryS3 implements InvocationHandler {
        private final Map<String, byte[]> objects;
        private final List<String> headKeys = new ArrayList<>();
        private final List<String> rangeKeys = new ArrayList<>();

        private InMemoryS3(Map<String, byte[]> objects) {
            this.objects = Map.copyOf(objects);
        }

        private S3Client client() {
            return (S3Client) Proxy.newProxyInstance(
                    S3Client.class.getClassLoader(), new Class<?>[]{S3Client.class}, this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "headObject" -> head((HeadObjectRequest) arguments[0]);
                case "getObjectAsBytes" -> get((GetObjectRequest) arguments[0]);
                case "serviceName" -> "s3";
                case "close" -> null;
                case "toString" -> "InMemoryShapefileS3Client";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException("Unexpected S3Client method " + method);
            };
        }

        private HeadObjectResponse head(HeadObjectRequest request) {
            headKeys.add(request.key());
            byte[] bytes = requireObject(request.key());
            return HeadObjectResponse.builder()
                    .contentLength((long) bytes.length)
                    .eTag(etag(request.key()))
                    .build();
        }

        private ResponseBytes<GetObjectResponse> get(GetObjectRequest request) {
            rangeKeys.add(request.key());
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
