package cn.superhuang.datascalpel.taskengine.httpapi;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericHttpApiPullConnectorTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void keepsResultPaginationOffAsyncSubmissionAndStatusRequests() throws Exception {
        AtomicInteger submissionRequests = new AtomicInteger();
        AtomicInteger statusRequests = new AtomicInteger();
        AtomicInteger resultRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                if ("/exports".equals(path)) {
                    submissionRequests.incrementAndGet();
                    assertNull(exchange.getRequestURI().getRawQuery());
                    respond(exchange, "{\"jobId\":\"42\"}");
                } else if ("/exports/42".equals(path)) {
                    statusRequests.incrementAndGet();
                    assertNull(exchange.getRequestURI().getRawQuery());
                    respond(exchange, "{\"status\":\"DONE\"}");
                } else if ("/exports/42/result".equals(path)) {
                    resultRequests.incrementAndGet();
                    assertEquals("page=0&size=10", exchange.getRequestURI().getRawQuery());
                    assertEquals("0", exchange.getRequestHeaders().getFirst("X-Page"));
                    respond(exchange, "{\"items\":[{\"id\":7}],\"totalPages\":1}");
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();

        HttpApiContracts.RequestTemplate submit = new HttpApiContracts.RequestTemplate(
                HttpApiContracts.HttpMethod.POST, "/exports", List.of(), List.of(), "{}");
        HttpApiContracts.AsyncJobConfiguration async = new HttpApiContracts.AsyncJobConfiguration(
                new HttpApiContracts.RequestTemplate(
                        HttpApiContracts.HttpMethod.GET, "/exports/${jobId}", List.of(), List.of(), null),
                "/jobId", "/status", Set.of("RUNNING"), Set.of("DONE"), Set.of("FAILED"),
                100, 5_000,
                new HttpApiContracts.RequestTemplate(
                        HttpApiContracts.HttpMethod.GET, "/exports/${jobId}/result", List.of(),
                        List.of(new HttpApiContracts.NamedValue("X-Page", "${page}")), null)
        );
        HttpApiContracts.ResourceDefinition resource = new HttpApiContracts.ResourceDefinition(
                UUID.randomUUID(), UUID.randomUUID(), "export", "Export",
                HttpApiContracts.GENERIC_CONNECTOR, true, submit,
                HttpApiContracts.SigningConfiguration.none(), HttpApiContracts.InvocationType.ASYNC_JOB,
                new HttpApiContracts.PageNumberPagination(
                        HttpApiContracts.ValueLocation.QUERY, "page", "size", 0, 10, null, "/totalPages"),
                async, "/items",
                List.of(new HttpApiContracts.OutputField(
                        "id", "/id", PlatformTypeDefinition.of(PlatformDataType.INTEGER), false, null)),
                new HttpApiContracts.ExecutionLimits(5, 100, 1024 * 1024, 30)
        );
        HttpApiContracts.RuntimeConnection connection = new HttpApiContracts.RuntimeConnection(
                new HttpApiContracts.ConnectionConfiguration(
                        "http://127.0.0.1:" + server.getAddress().getPort(), List.of(),
                        2_000, 5_000, 0, 0, new HttpApiContracts.NoneAuthentication(), false, false),
                new HttpApiContracts.CredentialBundle(null, null, null, null, null, null, null)
        );

        HttpApiPullResult result = new GenericHttpApiPullConnector().pull(
                new HttpApiContracts.PullRequest(connection, resource, List.of()));

        assertEquals(1, submissionRequests.get());
        assertEquals(1, statusRequests.get());
        assertEquals(1, resultRequests.get());
        assertEquals(List.of(List.of(7)), result.rows());
    }

    @Test
    void reportsAsyncFailureUnknownStatusAndPollingTimeout() throws Exception {
        startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/submit".equals(path)) {
                respond(exchange, 200, "{\"jobId\":\"job-1\"}");
            } else if (path.startsWith("/failed/")) {
                respond(exchange, 200, "{\"status\":\"FAILED\"}");
            } else if (path.startsWith("/unknown/")) {
                respond(exchange, 200, "{\"status\":\"MYSTERY\"}");
            } else if (path.startsWith("/timeout/")) {
                respond(exchange, 200, "{\"status\":\"RUNNING\"}");
            } else {
                respond(exchange, 404, "{}");
            }
        });
        HttpApiContracts.RuntimeConnection connection = connection();

        assertFailureCode("API_ASYNC_JOB_FAILED", () -> new GenericHttpApiPullConnector().pull(
                new HttpApiContracts.PullRequest(connection, asyncResource("failed", 5_000), List.of())));
        assertFailureCode("API_ASYNC_STATUS_UNKNOWN", () -> new GenericHttpApiPullConnector().pull(
                new HttpApiContracts.PullRequest(connection, asyncResource("unknown", 5_000), List.of())));
        assertFailureCode("API_ASYNC_TIMEOUT", () -> new GenericHttpApiPullConnector().pull(
                new HttpApiContracts.PullRequest(connection, asyncResource("timeout", 100), List.of())));
    }

    @Test
    void refreshesRuntimeTokenOnceAndReusesItForRetryAndLaterPulls() throws Exception {
        AtomicInteger tokenRequests = new AtomicInteger();
        AtomicInteger resourceRequests = new AtomicInteger();
        List<String> authorizations = new java.util.concurrent.CopyOnWriteArrayList<>();
        startServer(exchange -> {
            if ("/token".equals(exchange.getRequestURI().getPath())) {
                int request = tokenRequests.incrementAndGet();
                respond(exchange, 200, "{\"token\":\"runtime-" + request + "\",\"ttl\":3600}");
                return;
            }
            int request = resourceRequests.incrementAndGet();
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            if (request == 1) {
                respond(exchange, 401, "{\"error\":\"expired\"}");
            } else if (request == 2) {
                exchange.getResponseHeaders().set("Retry-After", "0");
                respond(exchange, 503, "{\"error\":\"retry\"}");
            } else {
                respond(exchange, 200, "{\"items\":[{\"id\":7}]}");
            }
        });
        HttpApiContracts.TokenEndpointAuthentication authentication =
                new HttpApiContracts.TokenEndpointAuthentication(
                        baseUrl() + "/token", HttpApiContracts.HttpMethod.POST, List.of(),
                        "{\"password\":\"${credential.password}\"}", "runner", "/token", "/ttl", null,
                        HttpApiContracts.ValueLocation.HEADER, "Authorization", "Bearer ${token}", true);
        HttpApiContracts.RuntimeConnection connection = connection(
                authentication,
                new HttpApiContracts.CredentialBundle(null, null, null, null,
                        "runtime-password", null, null), 1);
        HttpApiContracts.ResourceDefinition resource = resource(
                "/items", new HttpApiContracts.NoPagination(), integerField(), defaultLimits());
        GenericHttpApiPullConnector connector = new GenericHttpApiPullConnector();

        connector.pull(new HttpApiContracts.PullRequest(connection, resource, List.of()));
        connector.pull(new HttpApiContracts.PullRequest(connection, resource, List.of()));

        assertEquals(2, tokenRequests.get());
        assertEquals(List.of("Bearer runtime-1", "Bearer runtime-2", "Bearer runtime-2", "Bearer runtime-2"),
                authorizations);
    }

    @Test
    void signsFinalPaginationStateAndResignsRetries() throws Exception {
        String secret = "runner-signing-secret";
        AtomicInteger attempts = new AtomicInteger();
        List<String> signatures = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<String> nonces = new java.util.concurrent.CopyOnWriteArrayList<>();
        startServer(exchange -> {
            String nonce = exchange.getRequestHeaders().getFirst("X-Nonce");
            String signature = exchange.getRequestHeaders().getFirst("X-Signature");
            String canonical = exchange.getRequestMethod() + "\n"
                    + exchange.getRequestURI().getRawPath() + "\n"
                    + exchange.getRequestURI().getRawQuery() + "\n" + nonce;
            assertEquals(hmacHex(secret, canonical), signature);
            assertEquals("page=0&size=10", exchange.getRequestURI().getRawQuery());
            nonces.add(nonce);
            signatures.add(signature);
            if (attempts.incrementAndGet() == 1) {
                exchange.getResponseHeaders().set("Retry-After", "0");
                respond(exchange, 503, "{\"error\":\"retry\"}");
            } else {
                respond(exchange, 200, "{\"items\":[{\"id\":7}],\"totalPages\":1}");
            }
        });
        HttpApiContracts.SigningConfiguration signing = new HttpApiContracts.SigningConfiguration(
                HttpApiContracts.SignatureType.HMAC_SHA256,
                "${request.method}\n${request.path}\n${request.query}\n${nonce}", null,
                new HttpApiContracts.NonceConfiguration("X-Nonce", HttpApiContracts.ValueLocation.HEADER),
                new HttpApiContracts.SignatureOutput(
                        "X-Signature", HttpApiContracts.ValueLocation.HEADER,
                        HttpApiContracts.SignatureEncoding.HEX_LOWERCASE, null));
        HttpApiContracts.ResourceDefinition resource = resource(
                request("/items"), signing, HttpApiContracts.InvocationType.PAGINATED_REQUEST,
                new HttpApiContracts.PageNumberPagination(
                        HttpApiContracts.ValueLocation.QUERY, "page", "size", 0, 10, null, "/totalPages"),
                integerField(), defaultLimits());

        HttpApiPullResult result = new GenericHttpApiPullConnector().pull(new HttpApiContracts.PullRequest(
                connection(new HttpApiContracts.NoneAuthentication(),
                        new HttpApiContracts.CredentialBundle(null, null, null, null, null, secret, null), 1),
                resource, List.of()));

        assertEquals(List.of(List.of(7)), result.rows());
        assertEquals(2, attempts.get());
        assertNotEquals(nonces.get(0), nonces.get(1));
        assertNotEquals(signatures.get(0), signatures.get(1));
    }

    @Test
    void advancesOffsetCursorAndNextUrlPagination() throws Exception {
        List<String> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
        startServer(exchange -> {
            String pathAndQuery = exchange.getRequestURI().getRawPath()
                    + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery());
            requests.add(pathAndQuery);
            if (pathAndQuery.startsWith("/offset")) {
                respond(exchange, 200, pathAndQuery.contains("offset=2")
                        ? "{\"items\":[{\"id\":2},{\"id\":3}],\"total\":6}"
                        : "{\"items\":[{\"id\":4},{\"id\":5}],\"total\":6}");
            } else if (pathAndQuery.startsWith("/cursor")) {
                respond(exchange, 200, pathAndQuery.contains("cursor=first")
                        ? "{\"items\":[{\"id\":1}],\"cursor\":\"second\",\"more\":true}"
                        : "{\"items\":[{\"id\":2}],\"more\":false}");
            } else if (pathAndQuery.equals("/next")) {
                respond(exchange, 200, "{\"items\":[{\"id\":1}],\"next\":\"/next?p=2\"}");
            } else {
                respond(exchange, 200, "{\"items\":[{\"id\":2}],\"next\":null}");
            }
        });
        HttpApiContracts.RuntimeConnection connection = connection();
        GenericHttpApiPullConnector connector = new GenericHttpApiPullConnector();

        HttpApiPullResult offset = connector.pull(new HttpApiContracts.PullRequest(connection,
                resource("/offset", new HttpApiContracts.OffsetLimitPagination(
                        HttpApiContracts.ValueLocation.QUERY, "offset", "limit", 2, 2, null, "/total"),
                        integerField(), defaultLimits()), List.of()));
        HttpApiPullResult cursor = connector.pull(new HttpApiContracts.PullRequest(connection,
                resource("/cursor", new HttpApiContracts.CursorPagination(
                        HttpApiContracts.ValueLocation.QUERY, "cursor", "first", "/cursor", "/more"),
                        integerField(), defaultLimits()), List.of()));
        HttpApiPullResult next = connector.pull(new HttpApiContracts.PullRequest(connection,
                resource("/next", new HttpApiContracts.NextUrlPagination("/next", true),
                        integerField(), defaultLimits()), List.of()));

        assertEquals(List.of(List.of(2), List.of(3), List.of(4), List.of(5)), offset.rows());
        assertEquals(List.of(List.of(1), List.of(2)), cursor.rows());
        assertEquals(List.of(List.of(1), List.of(2)), next.rows());
        assertEquals(List.of(
                "/offset?offset=2&limit=2", "/offset?offset=4&limit=2",
                "/cursor?cursor=first", "/cursor?cursor=second", "/next", "/next?p=2"), requests);
    }

    @Test
    void pullsPagesAsBoundedBatchesAndReportsSummary() throws Exception {
        String firstPage = integerItems(0, GenericHttpApiPullConnector.MAX_BATCH_ROWS + 1, 2);
        String secondPage = integerItems(GenericHttpApiPullConnector.MAX_BATCH_ROWS + 1, 1, 2);
        AtomicInteger requests = new AtomicInteger();
        startServer(exchange -> {
            requests.incrementAndGet();
            String query = exchange.getRequestURI().getRawQuery();
            respond(exchange, query != null && query.contains("page=1") ? firstPage : secondPage);
        });
        HttpApiContracts.ResourceDefinition resource = resource(
                "/items",
                new HttpApiContracts.PageNumberPagination(
                        HttpApiContracts.ValueLocation.QUERY,
                        "page",
                        "size",
                        1,
                        GenericHttpApiPullConnector.MAX_BATCH_ROWS + 1,
                        null,
                        "/totalPages"),
                integerField(),
                new HttpApiContracts.ExecutionLimits(
                        2,
                        GenericHttpApiPullConnector.MAX_BATCH_ROWS + 2,
                        2 * 1024 * 1024,
                        30));
        List<HttpApiPullBatch> batches = new ArrayList<>();

        HttpApiPullSummary summary = new GenericHttpApiPullConnector().pullBatches(
                new HttpApiContracts.PullRequest(connection(), resource, List.of()),
                batches::add
        );

        assertEquals(2, requests.get());
        assertEquals(2, summary.pages());
        assertEquals(3, summary.batches());
        assertEquals(GenericHttpApiPullConnector.MAX_BATCH_ROWS + 2L, summary.rows());
        assertEquals(
                firstPage.getBytes(StandardCharsets.UTF_8).length
                        + secondPage.getBytes(StandardCharsets.UTF_8).length,
                summary.responseBytes());
        assertEquals(List.of(1, 2, 3), batches.stream().map(HttpApiPullBatch::batchNumber).toList());
        assertEquals(List.of(1, 1, 2), batches.stream().map(HttpApiPullBatch::pageNumber).toList());
        assertEquals(
                List.of(GenericHttpApiPullConnector.MAX_BATCH_ROWS, 1, 1),
                batches.stream().map(batch -> batch.rows().size()).toList());
        assertTrue(batches.stream().allMatch(
                batch -> batch.rows().size() <= GenericHttpApiPullConnector.MAX_BATCH_ROWS));
        assertEquals(0, batches.getFirst().rows().getFirst().getFirst());
        assertEquals(GenericHttpApiPullConnector.MAX_BATCH_ROWS + 1,
                batches.getLast().rows().getFirst().getFirst());
    }

    @Test
    void stopsBeforeTheNextPageWhenBatchConsumerFails() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        startServer(exchange -> {
            requests.incrementAndGet();
            respond(exchange, "{\"items\":[{\"id\":1}],\"totalPages\":2}");
        });
        HttpApiContracts.ResourceDefinition resource = resource(
                "/items",
                new HttpApiContracts.PageNumberPagination(
                        HttpApiContracts.ValueLocation.QUERY, "page", "size", 1, 1, null, "/totalPages"),
                integerField(),
                defaultLimits());

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                new GenericHttpApiPullConnector().pullBatches(
                        new HttpApiContracts.PullRequest(connection(), resource, List.of()),
                        ignored -> {
                            throw new IllegalStateException("batch consumer failed");
                        }));

        assertEquals("batch consumer failed", failure.getMessage());
        assertEquals(1, requests.get());
    }

    @Test
    void neverEmitsTheRecordBeyondTheMaximumRowLimit() throws Exception {
        startServer(exchange -> respond(exchange, integerItems(
                0, GenericHttpApiPullConnector.MAX_BATCH_ROWS + 1, 1)));
        HttpApiContracts.ResourceDefinition resource = resource(
                "/items",
                new HttpApiContracts.NoPagination(),
                integerField(),
                new HttpApiContracts.ExecutionLimits(
                        1,
                        GenericHttpApiPullConnector.MAX_BATCH_ROWS,
                        2 * 1024 * 1024,
                        30));
        AtomicInteger emittedRows = new AtomicInteger();

        assertFailureCode("API_MAX_ROWS_EXCEEDED", () ->
                new GenericHttpApiPullConnector().pullBatches(
                        new HttpApiContracts.PullRequest(connection(), resource, List.of()),
                        batch -> emittedRows.addAndGet(batch.rows().size())));

        assertEquals(GenericHttpApiPullConnector.MAX_BATCH_ROWS, emittedRows.get());
    }

    @Test
    void keepsLegacyConnectorsCompatibleThroughOneAggregateBatch() {
        HttpApiPullConnector legacy = new HttpApiPullConnector() {
            @Override
            public String type() {
                return "LEGACY";
            }

            @Override
            public HttpApiPullResult pull(HttpApiContracts.PullRequest request) {
                return new HttpApiPullResult(List.of(List.of(1), List.of(2)), 2, 128);
            }
        };
        List<HttpApiPullBatch> batches = new ArrayList<>();

        HttpApiPullSummary summary = legacy.pullBatches(null, batches::add);

        assertEquals(new HttpApiPullSummary(2, 1, 2, 128), summary);
        assertEquals(1, batches.size());
        assertEquals(0, batches.getFirst().pageNumber());
        assertEquals(List.of(List.of(1), List.of(2)), batches.getFirst().rows());
    }

    @Test
    void rejectsUnsafePaginationAndEnforcesResponseLimits() throws Exception {
        startServer(exchange -> {
            switch (exchange.getRequestURI().getPath()) {
                case "/cursor-loop" -> respond(exchange, 200,
                        "{\"items\":[{\"id\":1}],\"cursor\":\"same\"}");
                case "/cross-origin" -> respond(exchange, 200,
                        "{\"items\":[{\"id\":1}],\"next\":\"http://example.invalid/items\"}");
                case "/rows" -> respond(exchange, 200,
                        "{\"items\":[{\"id\":1},{\"id\":2}]}");
                case "/pages" -> respond(exchange, 200,
                        "{\"items\":[{\"id\":1}],\"totalPages\":3}");
                case "/slow-pages" -> {
                    Thread.sleep(1_050);
                    respond(exchange, 200, "{\"items\":[{\"id\":1}],\"totalPages\":2}");
                }
                default -> respond(exchange, 200,
                        "{\"items\":[{\"id\":123456789}]}");
            }
        });
        HttpApiContracts.RuntimeConnection connection = connection();

        assertFailureCode("API_PAGINATION_LOOP", () -> new GenericHttpApiPullConnector().pull(
                new HttpApiContracts.PullRequest(connection, resource(
                        "/cursor-loop", new HttpApiContracts.CursorPagination(
                                HttpApiContracts.ValueLocation.QUERY, "cursor", null, "/cursor", null),
                        integerField(), defaultLimits()), List.of())));
        assertFailureCode("API_NEXT_URL_ORIGIN_REJECTED", () -> new GenericHttpApiPullConnector().pull(
                new HttpApiContracts.PullRequest(connection, resource(
                        "/cross-origin", new HttpApiContracts.NextUrlPagination("/next", true),
                        integerField(), defaultLimits()), List.of())));
        assertFailureCode("API_MAX_ROWS_EXCEEDED", () -> new GenericHttpApiPullConnector().pull(
                new HttpApiContracts.PullRequest(connection, resource(
                        "/rows", new HttpApiContracts.NoPagination(), integerField(),
                        new HttpApiContracts.ExecutionLimits(2, 1, 1024, 30)), List.of())));
        assertFailureCode("API_MAX_RESPONSE_BYTES_EXCEEDED", () -> new GenericHttpApiPullConnector().pull(
                new HttpApiContracts.PullRequest(connection, resource(
                        "/bytes", new HttpApiContracts.NoPagination(), integerField(),
                        new HttpApiContracts.ExecutionLimits(2, 10, 16, 30)), List.of())));
        assertFailureCode("API_MAX_PAGES_EXCEEDED", () -> new GenericHttpApiPullConnector().pull(
                new HttpApiContracts.PullRequest(connection, resource(
                        "/pages", new HttpApiContracts.PageNumberPagination(
                                HttpApiContracts.ValueLocation.QUERY,
                                "page", "size", 1, 1, null, "/totalPages"),
                        integerField(), new HttpApiContracts.ExecutionLimits(1, 10, 1024, 30)), List.of())));
        assertFailureCode("API_MAX_DURATION_EXCEEDED", () -> new GenericHttpApiPullConnector().pull(
                new HttpApiContracts.PullRequest(connection, resource(
                        "/slow-pages", new HttpApiContracts.PageNumberPagination(
                                HttpApiContracts.ValueLocation.QUERY,
                                "page", "size", 1, 1, null, "/totalPages"),
                        integerField(), new HttpApiContracts.ExecutionLimits(2, 10, 1024, 1)), List.of())));
    }

    @Test
    void rejectsLossyOrInvalidOutputConversions() throws Exception {
        startServer(exchange -> {
            String value = switch (exchange.getRequestURI().getPath()) {
                case "/integer" -> "2147483648";
                case "/decimal" -> "1.234";
                case "/string" -> "\"abcd\"";
                case "/boolean" -> "\"yes\"";
                case "/date" -> "\"2026-99-01\"";
                case "/timestamp" -> "\"not-a-timestamp\"";
                default -> "null";
            };
            respond(exchange, 200, "{\"items\":[{\"value\":" + value + "}]}");
        });
        List<ConversionCase> cases = List.of(
                new ConversionCase("/integer", PlatformTypeDefinition.of(PlatformDataType.INTEGER)),
                new ConversionCase("/decimal", PlatformTypeDefinition.decimal(3, 2)),
                new ConversionCase("/string", PlatformTypeDefinition.string(3)),
                new ConversionCase("/boolean", PlatformTypeDefinition.of(PlatformDataType.BOOLEAN)),
                new ConversionCase("/date", PlatformTypeDefinition.of(PlatformDataType.DATE)),
                new ConversionCase("/timestamp", PlatformTypeDefinition.of(PlatformDataType.TIMESTAMP)));
        HttpApiContracts.RuntimeConnection connection = connection();
        List<String> failedPaths = new ArrayList<>();

        for (ConversionCase conversion : cases) {
            HttpApiPullException failure = assertThrows(HttpApiPullException.class, () ->
                    new GenericHttpApiPullConnector().pull(new HttpApiContracts.PullRequest(
                            connection,
                            resource(conversion.path(), new HttpApiContracts.NoPagination(),
                                    new HttpApiContracts.OutputField(
                                            "value", "/value", conversion.type(), false, null),
                                    defaultLimits()),
                            List.of())));
            assertEquals("API_FIELD_CONVERSION_FAILED", failure.code());
            failedPaths.add(conversion.path());
        }

        assertEquals(cases.stream().map(ConversionCase::path).toList(), failedPaths);
    }

    private HttpApiContracts.RuntimeConnection connection() {
        return connection(new HttpApiContracts.NoneAuthentication(),
                new HttpApiContracts.CredentialBundle(null, null, null, null, null, null, null), 0);
    }

    private HttpApiContracts.RuntimeConnection connection(
            HttpApiContracts.AuthenticationConfiguration authentication,
            HttpApiContracts.CredentialBundle credentials,
            int maxRetries
    ) {
        return new HttpApiContracts.RuntimeConnection(
                new HttpApiContracts.ConnectionConfiguration(
                        baseUrl(), List.of(), 2_000, 5_000, 0, maxRetries,
                        authentication, credentials.signingSecret() != null, credentials.signingPrivateKey() != null),
                credentials);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static HttpApiContracts.RequestTemplate request(String path) {
        return new HttpApiContracts.RequestTemplate(
                HttpApiContracts.HttpMethod.GET, path, List.of(), List.of(), null);
    }

    private static HttpApiContracts.ResourceDefinition resource(
            String path,
            HttpApiContracts.PaginationConfiguration pagination,
            HttpApiContracts.OutputField field,
            HttpApiContracts.ExecutionLimits limits
    ) {
        return resource(request(path), HttpApiContracts.SigningConfiguration.none(),
                pagination instanceof HttpApiContracts.NoPagination
                        ? HttpApiContracts.InvocationType.SINGLE_REQUEST
                        : HttpApiContracts.InvocationType.PAGINATED_REQUEST,
                pagination, field, limits);
    }

    private static HttpApiContracts.ResourceDefinition resource(
            HttpApiContracts.RequestTemplate request,
            HttpApiContracts.SigningConfiguration signing,
            HttpApiContracts.InvocationType invocationType,
            HttpApiContracts.PaginationConfiguration pagination,
            HttpApiContracts.OutputField field,
            HttpApiContracts.ExecutionLimits limits
    ) {
        return new HttpApiContracts.ResourceDefinition(
                UUID.randomUUID(), UUID.randomUUID(), "resource", "Resource",
                HttpApiContracts.GENERIC_CONNECTOR, true, request, signing, invocationType,
                pagination, null, "/items", List.of(field), limits);
    }

    private static HttpApiContracts.ResourceDefinition asyncResource(String statusPath, int pollingTimeoutMs) {
        HttpApiContracts.RequestTemplate submission = new HttpApiContracts.RequestTemplate(
                HttpApiContracts.HttpMethod.POST, "/submit", List.of(), List.of(), "{}");
        HttpApiContracts.AsyncJobConfiguration async = new HttpApiContracts.AsyncJobConfiguration(
                new HttpApiContracts.RequestTemplate(
                        HttpApiContracts.HttpMethod.GET,
                        "/" + statusPath + "/${jobId}",
                        List.of(),
                        List.of(),
                        null),
                "/jobId",
                "/status",
                Set.of("RUNNING"),
                Set.of("SUCCESS"),
                Set.of("FAILED"),
                50,
                pollingTimeoutMs,
                new HttpApiContracts.RequestTemplate(
                        HttpApiContracts.HttpMethod.GET,
                        "/result/${jobId}",
                        List.of(),
                        List.of(),
                        null));
        return new HttpApiContracts.ResourceDefinition(
                UUID.randomUUID(), UUID.randomUUID(), "async-resource", "Async Resource",
                HttpApiContracts.GENERIC_CONNECTOR, true, submission,
                HttpApiContracts.SigningConfiguration.none(), HttpApiContracts.InvocationType.ASYNC_JOB,
                new HttpApiContracts.NoPagination(), async, "/items", List.of(integerField()), defaultLimits());
    }

    private static HttpApiContracts.OutputField integerField() {
        return new HttpApiContracts.OutputField(
                "id", "/id", PlatformTypeDefinition.of(PlatformDataType.INTEGER), false, null);
    }

    private static HttpApiContracts.ExecutionLimits defaultLimits() {
        return new HttpApiContracts.ExecutionLimits(10, 100, 1024 * 1024, 30);
    }

    private static String integerItems(int first, int count, int totalPages) {
        StringBuilder body = new StringBuilder("{\"items\":[");
        for (int index = 0; index < count; index++) {
            if (index > 0) body.append(',');
            body.append("{\"id\":").append(first + index).append('}');
        }
        return body.append("],\"totalPages\":").append(totalPages).append('}').toString();
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception exception) {
                throw exception instanceof IOException io ? io : new IOException(exception);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        respond(exchange, 200, body);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static String hmacHex(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder result = new StringBuilder();
        for (byte item : mac.doFinal(value.getBytes(StandardCharsets.UTF_8))) {
            result.append(String.format("%02x", item));
        }
        return result.toString();
    }

    private static void assertFailureCode(String code, org.junit.jupiter.api.function.Executable executable) {
        assertEquals(code, assertThrows(HttpApiPullException.class, executable).code());
    }

    private record ConversionCase(String path, PlatformTypeDefinition type) {
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
