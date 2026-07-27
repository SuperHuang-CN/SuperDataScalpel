package cn.superhuang.data.scalpel.filegdb.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.filegdb.FileGdbReadOptions;
import cn.superhuang.data.scalpel.filegdb.FileGeodatabase;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolyline;
import cn.superhuang.data.scalpel.filegdb.testutil.TestFileGdbBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.http.apache5.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

class S3HttpRangeIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void standardS3ClientReadsFixtureUsingOnlyHeadAndRangeGet() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        var geometryFixture = TestFileGdbBuilder.multiPointsAndPolylines(temporaryDirectory);
        String bucket = "test-bucket";
        String prefix = "datasets/scalar-and-point.gdb";
        Map<String, byte[]> objects = objects(fixture.directory(), prefix);
        String geometryPrefix = "datasets/multipoint-and-polyline.gdb";
        objects.putAll(objects(geometryFixture.directory(), geometryPrefix));
        RangeEndpoint endpoint = new RangeEndpoint(bucket, objects);

        try {
            endpoint.start();
            try (S3Client client = S3Client.builder()
                    .endpointOverride(endpoint.uri())
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                    .httpClientBuilder(Apache5HttpClient.builder()
                            .proxyConfiguration(ProxyConfiguration.builder()
                                    .useSystemPropertyValues(false)
                                    .useEnvironmentVariableValues(false)
                                    .build()))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .build()) {
                try (FileGeodatabase database = FileGeodatabase.open(S3FileGdbSource.create(
                                client,
                                new S3FileGdbLocation(bucket, prefix),
                                new S3FileGdbOptions(64 * 1024, 4L * 64 * 1024)));
                        var cursor = database.openCursor(
                                fixture.layerId("ScalarPointXY"), FileGdbReadOptions.limit(1))) {
                    assertEquals(4, database.layers().size());
                    assertEquals("中文 café", cursor.next().attribute("Name"));
                }

                try (FileGeodatabase database = FileGeodatabase.open(S3FileGdbSource.create(
                                client,
                                new S3FileGdbLocation(bucket, geometryPrefix),
                                new S3FileGdbOptions(64 * 1024, 4L * 64 * 1024)));
                        var cursor = database.openCursor(
                                geometryFixture.layerId("PolylineXYZM"), FileGdbReadOptions.limit(1))) {
                    assertEquals(8, database.layers().size());
                    FileGdbPolyline polyline = assertInstanceOf(
                            FileGdbPolyline.class, cursor.next().geometry());
                    assertEquals(2, polyline.pathCount());
                    assertEquals(5, polyline.coordinates().size());
                }

                assertNotNull(client.headObject(request -> request
                        .bucket(bucket)
                        .key(prefix + "/gdb")));
            }
        } catch (RuntimeException | Error failure) {
            if (endpoint.handlerFailure != null) {
                failure.addSuppressed(endpoint.handlerFailure);
            }
            throw failure;
        } finally {
            endpoint.close();
        }

        assertTrue(endpoint.methods.stream().allMatch(method -> method.equals("HEAD") || method.equals("GET")));
        assertFalse(endpoint.getRanges.isEmpty());
        assertEquals(endpoint.getRanges.size(), endpoint.ifMatches.size());
        assertTrue(endpoint.getRanges.stream().allMatch(range -> range.matches("bytes=\\d+-\\d+")));
        assertTrue(endpoint.ifMatches.stream().allMatch(value -> value != null && !value.isBlank()));
        assertEquals(0, endpoint.fullObjectGets);
    }

    private static Map<String, byte[]> objects(Path directory, String prefix) throws IOException {
        Map<String, byte[]> objects = new LinkedHashMap<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                objects.put(prefix + "/" + file.getFileName(), Files.readAllBytes(file));
            }
        }
        return objects;
    }

    private static final class RangeEndpoint implements AutoCloseable {
        private final String bucket;
        private final Map<String, byte[]> objects;
        private final List<String> methods = new ArrayList<>();
        private final List<String> getRanges = new ArrayList<>();
        private final List<String> ifMatches = new ArrayList<>();
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private HttpServer server;
        private int fullObjectGets;
        private volatile Throwable handlerFailure;

        private RangeEndpoint(String bucket, Map<String, byte[]> objects) {
            this.bucket = bucket;
            this.objects = objects;
        }

        private void start() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.setExecutor(executor);
            server.start();
        }

        private URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                try {
                    handleRequest(exchange);
                } catch (Throwable failure) {
                    handlerFailure = failure;
                    exchange.sendResponseHeaders(500, -1);
                }
            }
        }

        private void handleRequest(HttpExchange exchange) throws IOException {
                String method = exchange.getRequestMethod();
                methods.add(method);
                String path = exchange.getRequestURI().getPath();
                String bucketPrefix = "/" + bucket + "/";
                if (!path.startsWith(bucketPrefix)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                String key = path.substring(bucketPrefix.length());
                byte[] object = objects.get(key);
                if (object == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                String eTag = "\"etag-" + Integer.toHexString(key.hashCode()) + "\"";
                exchange.getResponseHeaders().set("ETag", eTag);
                exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                if (method.equals("HEAD")) {
                    exchange.getResponseHeaders().set("Content-Length", Integer.toString(object.length));
                    exchange.sendResponseHeaders(200, -1);
                    return;
                }
                if (!method.equals("GET")) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String range = exchange.getRequestHeaders().getFirst("Range");
                if (range == null) {
                    fullObjectGets++;
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                String ifMatch = exchange.getRequestHeaders().getFirst("If-Match");
                getRanges.add(range);
                ifMatches.add(ifMatch);
                if (!eTag.equals(ifMatch)) {
                    exchange.sendResponseHeaders(412, -1);
                    return;
                }
                String[] bounds = range.substring("bytes=".length()).split("-", 2);
                int start = Integer.parseInt(bounds[0]);
                int end = Integer.parseInt(bounds[1]);
                if (start < 0 || end < start || end >= object.length) {
                    exchange.sendResponseHeaders(416, -1);
                    return;
                }
                int length = end - start + 1;
                exchange.getResponseHeaders().set(
                        "Content-Range", "bytes " + start + "-" + end + "/" + object.length);
                exchange.sendResponseHeaders(206, length);
                exchange.getResponseBody().write(object, start, length);
        }

        @Override
        public void close() {
            if (server != null) {
                server.stop(0);
            }
            executor.close();
        }
    }
}
