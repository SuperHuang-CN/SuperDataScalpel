package cn.superhuang.data.scalpel.shapefile.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.shapefile.ShapefileDataset;
import cn.superhuang.data.scalpel.shapefile.ShapefileReadOptions;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePoint;
import cn.superhuang.data.scalpel.shapefile.testutil.TestShapefileBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
    void standardS3ClientReadsAllComponentsUsingOnlyHeadAndConditionalRanges() throws Exception {
        Path shp = new TestShapefileBuilder(
                        temporaryDirectory,
                        "roads",
                        ShapefileShapeType.POINT,
                        new ShapefileEnvelope(0, 0, 10, 10, 0, 0, 0, 0))
                .field("Name", 'C', 20, 0)
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 3, 4, null, null), false, "中文道路")
                .dbfCharset(StandardCharsets.UTF_8)
                .cpg("65001")
                .prj("LOCAL_CS[\"http-range\"]")
                .write();
        String bucket = "test-bucket";
        String prefix = "datasets/roads";
        RangeEndpoint endpoint = new RangeEndpoint(bucket, objects(shp, prefix));

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
                    .build();
                    ShapefileDataset dataset = ShapefileDataset.open(S3ShapefileSource.create(
                            client,
                            S3ShapefileLocation.fromShpKey(bucket, prefix + ".shp"),
                            new S3ShapefileOptions(64 * 1024, 4L * 64 * 1024)));
                    var cursor = dataset.openCursor(ShapefileReadOptions.limit(1))) {
                assertEquals(StandardCharsets.UTF_8, dataset.schema().dbfCharset());
                assertEquals("LOCAL_CS[\"http-range\"]", dataset.schema().spatialReference().wkt());
                var feature = cursor.next();
                assertEquals("中文道路", feature.attribute("Name"));
                ShapefilePoint point = assertInstanceOf(ShapefilePoint.class, feature.geometry());
                assertEquals(3, point.x());
                assertEquals(4, point.y());
            }
        } catch (RuntimeException | Error failure) {
            if (endpoint.handlerFailure != null) {
                failure.addSuppressed(endpoint.handlerFailure);
            }
            throw failure;
        } finally {
            endpoint.close();
        }

        assertEquals(5, endpoint.headCount);
        assertTrue(endpoint.methods.stream().allMatch(method -> method.equals("HEAD") || method.equals("GET")));
        assertFalse(endpoint.ranges.isEmpty());
        assertEquals(endpoint.ranges.size(), endpoint.ifMatches.size());
        assertTrue(endpoint.ranges.stream().allMatch(range -> range.matches("bytes=\\d+-\\d+")));
        assertTrue(endpoint.ifMatches.stream().allMatch(value -> value != null && !value.isBlank()));
        assertEquals(0, endpoint.fullObjectGets);
    }

    private static Map<String, byte[]> objects(Path shp, String prefix) throws IOException {
        Map<String, byte[]> objects = new LinkedHashMap<>();
        String stem = shp.getFileName().toString();
        stem = stem.substring(0, stem.length() - 4);
        try (var files = Files.list(shp.getParent())) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String extension = file.getFileName().toString().substring(stem.length());
                objects.put(prefix + extension, Files.readAllBytes(file));
            }
        }
        return objects;
    }

    private static final class RangeEndpoint implements AutoCloseable {
        private final String bucket;
        private final Map<String, byte[]> objects;
        private final List<String> methods = new ArrayList<>();
        private final List<String> ranges = new ArrayList<>();
        private final List<String> ifMatches = new ArrayList<>();
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private HttpServer server;
        private int headCount;
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
            String bucketPrefix = "/" + bucket + "/";
            String path = exchange.getRequestURI().getPath();
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
                headCount++;
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
            ranges.add(range);
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
