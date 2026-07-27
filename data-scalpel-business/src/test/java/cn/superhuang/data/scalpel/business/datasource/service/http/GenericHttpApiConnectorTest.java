package cn.superhuang.data.scalpel.business.datasource.service.http;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericHttpApiConnectorTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void supportsZeroBasedPageNumbersAndInitializesPaginationTemplateVariables() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        startServer(exchange -> {
            requests.incrementAndGet();
            String query = exchange.getRequestURI().getRawQuery();
            int page = query != null && query.contains("page=1") ? 1 : 0;
            assertEquals(Integer.toString(page), exchange.getRequestHeaders().getFirst("X-Rendered-Page"));
            respond(exchange, 200, """
                    {"items":[{"id":"page-%d"}],"totalPages":2}
                    """.formatted(page));
        });

        HttpApiContracts.ResourceDefinition resource = resource(
                new HttpApiContracts.RequestTemplate(
                        HttpApiContracts.HttpMethod.GET,
                        "/items",
                        List.of(),
                        List.of(new HttpApiContracts.NamedValue("X-Rendered-Page", "${page}")),
                        null
                ),
                HttpApiContracts.InvocationType.PAGINATED_REQUEST,
                new HttpApiContracts.PageNumberPagination(
                        HttpApiContracts.ValueLocation.QUERY,
                        "page", "size", 0, 1, null, "/totalPages"
                ),
                null
        );

        PullResult result = connector().pull(new HttpApiContracts.PullRequest(
                runtimeConnection(), resource, List.of()));

        assertEquals(2, requests.get());
        assertEquals(2, result.pageCount());
        assertEquals(List.of(List.of("page-0"), List.of("page-1")), result.rows());
    }

    @Test
    void appliesResultPaginationOnlyAfterAsyncSubmissionAndPolling() throws Exception {
        AtomicInteger submissionRequests = new AtomicInteger();
        AtomicInteger statusRequests = new AtomicInteger();
        AtomicInteger resultRequests = new AtomicInteger();
        startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/jobs".equals(path)) {
                submissionRequests.incrementAndGet();
                assertEquals(null, exchange.getRequestURI().getRawQuery());
                respond(exchange, 200, "{\"jobId\":\"job-42\"}");
                return;
            }
            if ("/jobs/job-42".equals(path)) {
                statusRequests.incrementAndGet();
                assertEquals(null, exchange.getRequestURI().getRawQuery());
                respond(exchange, 200, "{\"status\":\"SUCCESS\"}");
                return;
            }
            if ("/jobs/job-42/result".equals(path)) {
                resultRequests.incrementAndGet();
                assertEquals("page=1&size=100", exchange.getRequestURI().getRawQuery());
                respond(exchange, 200, "{\"items\":[{\"id\":\"done\"}],\"totalPages\":1}");
                return;
            }
            respond(exchange, 404, "{}");
        });

        HttpApiContracts.RequestTemplate submission = new HttpApiContracts.RequestTemplate(
                HttpApiContracts.HttpMethod.POST, "/jobs", List.of(), List.of(), "{}");
        HttpApiContracts.AsyncJobConfiguration async = new HttpApiContracts.AsyncJobConfiguration(
                new HttpApiContracts.RequestTemplate(
                        HttpApiContracts.HttpMethod.GET, "/jobs/${jobId}", List.of(), List.of(), null),
                "/jobId", "/status", Set.of("RUNNING"), Set.of("SUCCESS"), Set.of("FAILED"),
                100, 5_000,
                new HttpApiContracts.RequestTemplate(
                        HttpApiContracts.HttpMethod.GET, "/jobs/${jobId}/result", List.of(), List.of(), null)
        );
        HttpApiContracts.ResourceDefinition resource = resource(
                submission,
                HttpApiContracts.InvocationType.ASYNC_JOB,
                new HttpApiContracts.PageNumberPagination(
                        HttpApiContracts.ValueLocation.QUERY,
                        "page", "size", 1, 100, null, "/totalPages"),
                async
        );

        PullResult result = connector().pull(new HttpApiContracts.PullRequest(
                runtimeConnection(), resource, List.of()));

        assertEquals(1, submissionRequests.get());
        assertEquals(1, statusRequests.get());
        assertEquals(1, resultRequests.get());
        assertEquals(List.of(List.of("done")), result.rows());
    }

    @Test
    void reportsAsyncFailureUnknownStatusAndPollingTimeout() throws Exception {
        startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/submit")) {
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
        HttpApiContracts.RuntimeConnection connection = runtimeConnection();

        assertFailureCode("API_ASYNC_JOB_FAILED", () -> connector().pull(new HttpApiContracts.PullRequest(
                connection, asyncResource("failed", 5_000), List.of())));
        assertFailureCode("API_ASYNC_STATUS_UNKNOWN", () -> connector().pull(new HttpApiContracts.PullRequest(
                connection, asyncResource("unknown", 5_000), List.of())));
        assertFailureCode("API_ASYNC_TIMEOUT", () -> connector().pull(new HttpApiContracts.PullRequest(
                connection, asyncResource("timeout", 100), List.of())));
    }

    @Test
    void refreshesOAuthTokenOnceAndKeepsItForRetryAndLaterPulls() throws Exception {
        AtomicInteger tokenRequests = new AtomicInteger();
        AtomicInteger resourceRequests = new AtomicInteger();
        List<String> authorizations = new java.util.concurrent.CopyOnWriteArrayList<>();
        startServer(exchange -> {
            if ("/oauth/token".equals(exchange.getRequestURI().getPath())) {
                int request = tokenRequests.incrementAndGet();
                assertEquals("grant_type=client_credentials&client_id=client-id&client_secret=client-secret&scope=read",
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                respond(exchange, 200, "{\"access_token\":\"token-" + request + "\",\"expires_in\":3600}");
                return;
            }
            int request = resourceRequests.incrementAndGet();
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            authorizations.add(authorization);
            if (request == 1) {
                respond(exchange, 401, "{\"error\":\"expired\"}");
            } else if (request == 2) {
                exchange.getResponseHeaders().set("Retry-After", "0");
                respond(exchange, 503, "{\"error\":\"temporary\"}");
            } else {
                respond(exchange, 200, "{\"items\":[{\"id\":\"ok\"}]}");
            }
        });

        String tokenUrl = baseUrl() + "/oauth/token";
        HttpApiContracts.OAuth2ClientCredentialsAuthentication authentication =
                new HttpApiContracts.OAuth2ClientCredentialsAuthentication(
                        tokenUrl, "client-id", List.of("read"), null,
                        HttpApiContracts.ValueLocation.HEADER, "Authorization", "Bearer ${token}", true);
        HttpApiContracts.RuntimeConnection connection = runtimeConnection(
                authentication,
                new HttpApiContracts.CredentialBundle(null, null, null, "client-secret", null, null, null),
                1);
        HttpApiContracts.ResourceDefinition resource = resource(
                request(HttpApiContracts.HttpMethod.GET, "/items"),
                HttpApiContracts.InvocationType.SINGLE_REQUEST,
                new HttpApiContracts.NoPagination(), null);
        GenericHttpApiConnector connector = connector();

        assertEquals(List.of(List.of("ok")), connector.pull(
                new HttpApiContracts.PullRequest(connection, resource, List.of())).rows());
        assertEquals(List.of(List.of("ok")), connector.pull(
                new HttpApiContracts.PullRequest(connection, resource, List.of())).rows());

        assertEquals(2, tokenRequests.get());
        assertEquals(List.of("Bearer token-1", "Bearer token-2", "Bearer token-2", "Bearer token-2"),
                authorizations);
    }

    @Test
    void refreshesDynamicTokenAtMostOnceForRepeatedUnauthorizedResponses() throws Exception {
        AtomicInteger tokenRequests = new AtomicInteger();
        AtomicInteger resourceRequests = new AtomicInteger();
        startServer(exchange -> {
            if ("/oauth/token".equals(exchange.getRequestURI().getPath())) {
                int request = tokenRequests.incrementAndGet();
                respond(exchange, 200, "{\"access_token\":\"denied-" + request + "\",\"expires_in\":3600}");
                return;
            }
            resourceRequests.incrementAndGet();
            respond(exchange, 401, "{\"error\":\"still denied\"}");
        });
        HttpApiContracts.RuntimeConnection connection = runtimeConnection(
                new HttpApiContracts.OAuth2ClientCredentialsAuthentication(
                        baseUrl() + "/oauth/token", "client-id", List.of(), null,
                        HttpApiContracts.ValueLocation.HEADER, "Authorization", "Bearer ${token}", true),
                new HttpApiContracts.CredentialBundle(null, null, null, "client-secret", null, null, null),
                0);

        HttpApiExecutionException failure = assertThrows(HttpApiExecutionException.class, () -> connector().pull(
                new HttpApiContracts.PullRequest(connection, resource(
                        request(HttpApiContracts.HttpMethod.GET, "/items"),
                        HttpApiContracts.InvocationType.SINGLE_REQUEST,
                        new HttpApiContracts.NoPagination(), null), List.of())));

        assertEquals("API_AUTHENTICATION_FAILED", failure.code());
        assertEquals(2, tokenRequests.get());
        assertEquals(2, resourceRequests.get());
    }

    @Test
    void rendersAndCachesCustomTokenEndpointRequests() throws Exception {
        AtomicInteger tokenRequests = new AtomicInteger();
        AtomicInteger resourceRequests = new AtomicInteger();
        startServer(exchange -> {
            if ("/session".equals(exchange.getRequestURI().getPath())) {
                tokenRequests.incrementAndGet();
                assertEquals("department-a", exchange.getRequestHeaders().getFirst("X-Tenant"));
                assertEquals("{\"username\":\"operator\",\"password\":\"runtime-password\"}",
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                respond(exchange, 200, "{\"payload\":{\"token\":\"session-token\",\"ttl\":3600}}");
                return;
            }
            resourceRequests.incrementAndGet();
            assertEquals("auth=session-token", exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "{\"items\":[{\"id\":\"ok\"}]}");
        });
        HttpApiContracts.TokenEndpointAuthentication authentication =
                new HttpApiContracts.TokenEndpointAuthentication(
                        baseUrl() + "/session", HttpApiContracts.HttpMethod.POST,
                        List.of(new HttpApiContracts.NamedValue("X-Tenant", "${runtime.tenant}")),
                        "{\"username\":\"${credential.username}\",\"password\":\"${credential.password}\"}",
                        "operator", "/payload/token", "/payload/ttl", null,
                        HttpApiContracts.ValueLocation.QUERY, "auth", "${token}", true);
        HttpApiContracts.RuntimeConnection connection = runtimeConnection(
                authentication,
                new HttpApiContracts.CredentialBundle(null, null, null, null,
                        "runtime-password", null, null), 0);
        HttpApiContracts.PullRequest request = new HttpApiContracts.PullRequest(
                connection,
                resource(request(HttpApiContracts.HttpMethod.GET, "/items"),
                        HttpApiContracts.InvocationType.SINGLE_REQUEST,
                        new HttpApiContracts.NoPagination(), null),
                List.of(new HttpApiContracts.RuntimeParameter("tenant", "department-a")));
        GenericHttpApiConnector connector = connector();

        connector.pull(request);
        connector.pull(request);

        assertEquals(1, tokenRequests.get());
        assertEquals(2, resourceRequests.get());
    }

    @Test
    void signsTheFinalPaginatedRequestAndResignsEveryRetryWithHmac() throws Exception {
        String secret = "signing-secret";
        AtomicInteger attempts = new AtomicInteger();
        List<String> nonces = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<String> signatures = new java.util.concurrent.CopyOnWriteArrayList<>();
        startServer(exchange -> {
            String timestamp = exchange.getRequestHeaders().getFirst("X-Timestamp");
            String nonce = exchange.getRequestHeaders().getFirst("X-Nonce");
            String actual = exchange.getRequestHeaders().getFirst("X-Signature");
            String canonical = exchange.getRequestMethod() + "\n"
                    + exchange.getRequestURI().getRawPath() + "\n"
                    + exchange.getRequestURI().getRawQuery() + "\n"
                    + sha256Hex("") + "\n" + timestamp + "\n" + nonce;
            assertEquals(hmacHex(secret, canonical), actual);
            assertEquals("filter=active&page=1&size=1", exchange.getRequestURI().getRawQuery());
            nonces.add(nonce);
            signatures.add(actual);
            if (attempts.incrementAndGet() == 1) {
                exchange.getResponseHeaders().set("Retry-After", "0");
                respond(exchange, 503, "{\"error\":\"retry\"}");
            } else {
                respond(exchange, 200, "{\"items\":[{\"id\":\"signed\"}],\"totalPages\":1}");
            }
        });
        HttpApiContracts.SigningConfiguration signing = new HttpApiContracts.SigningConfiguration(
                HttpApiContracts.SignatureType.HMAC_SHA256,
                "${request.method}\n${request.path}\n${request.query}\n${request.bodyHash}\n${timestamp}\n${nonce}",
                new HttpApiContracts.TimestampConfiguration(
                        "X-Timestamp", HttpApiContracts.ValueLocation.HEADER,
                        HttpApiContracts.TimestampUnit.MILLISECONDS),
                new HttpApiContracts.NonceConfiguration("X-Nonce", HttpApiContracts.ValueLocation.HEADER),
                new HttpApiContracts.SignatureOutput(
                        "X-Signature", HttpApiContracts.ValueLocation.HEADER,
                        HttpApiContracts.SignatureEncoding.HEX_LOWERCASE, null));
        HttpApiContracts.ResourceDefinition resource = resource(
                new HttpApiContracts.RequestTemplate(
                        HttpApiContracts.HttpMethod.GET, "/items",
                        List.of(new HttpApiContracts.NamedValue("filter", "${runtime.filter}")), List.of(), null),
                signing, HttpApiContracts.InvocationType.PAGINATED_REQUEST,
                new HttpApiContracts.PageNumberPagination(
                        HttpApiContracts.ValueLocation.QUERY, "page", "size", 1, 1, null, "/totalPages"),
                null, "/items", defaultFields(), defaultLimits());
        HttpApiContracts.RuntimeConnection connection = runtimeConnection(
                new HttpApiContracts.NoneAuthentication(),
                new HttpApiContracts.CredentialBundle(null, null, null, null, null, secret, null), 1);

        PullResult result = connector().pull(new HttpApiContracts.PullRequest(
                connection, resource, List.of(new HttpApiContracts.RuntimeParameter("filter", "active"))));

        assertEquals(List.of(List.of("signed")), result.rows());
        assertEquals(2, attempts.get());
        assertNotEquals(nonces.get(0), nonces.get(1));
        assertNotEquals(signatures.get(0), signatures.get(1));
    }

    @Test
    void signsTheFinalRequestWithRsa() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String privateKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        startServer(exchange -> {
            String canonical = exchange.getRequestMethod() + "\n"
                    + exchange.getRequestURI().getRawPath() + "\n"
                    + exchange.getRequestURI().getRawQuery();
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(keyPair.getPublic());
            verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
            assertTrue(verifier.verify(Base64.getDecoder().decode(
                    exchange.getRequestHeaders().getFirst("X-RSA-Signature"))));
            assertEquals("tenant=alpha%20team", exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "{\"items\":[{\"id\":\"rsa\"}]}");
        });
        HttpApiContracts.SigningConfiguration signing = new HttpApiContracts.SigningConfiguration(
                HttpApiContracts.SignatureType.RSA_SHA256,
                "${request.method}\n${request.path}\n${request.query}", null, null,
                new HttpApiContracts.SignatureOutput(
                        "X-RSA-Signature", HttpApiContracts.ValueLocation.HEADER,
                        HttpApiContracts.SignatureEncoding.BASE64, null));
        HttpApiContracts.ResourceDefinition resource = resource(
                new HttpApiContracts.RequestTemplate(
                        HttpApiContracts.HttpMethod.GET, "/items",
                        List.of(new HttpApiContracts.NamedValue("tenant", "${runtime.tenant}")), List.of(), null),
                signing, HttpApiContracts.InvocationType.SINGLE_REQUEST,
                new HttpApiContracts.NoPagination(), null, "/items", defaultFields(), defaultLimits());
        HttpApiContracts.RuntimeConnection connection = runtimeConnection(
                new HttpApiContracts.NoneAuthentication(),
                new HttpApiContracts.CredentialBundle(null, null, null, null, null, null, privateKeyPem), 0);

        assertEquals(List.of(List.of("rsa")), connector().pull(new HttpApiContracts.PullRequest(
                connection, resource,
                List.of(new HttpApiContracts.RuntimeParameter("tenant", "alpha team")))).rows());
    }

    @Test
    void supportsOffsetCursorAndSameOriginNextUrlPagination() throws Exception {
        List<String> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
        startServer(exchange -> {
            String pathAndQuery = exchange.getRequestURI().getRawPath()
                    + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery());
            requests.add(pathAndQuery);
            if (pathAndQuery.startsWith("/offset")) {
                boolean first = pathAndQuery.contains("offset=5");
                respond(exchange, 200, first
                        ? "{\"items\":[{\"id\":\"o5\"},{\"id\":\"o6\"}],\"total\":9}"
                        : "{\"items\":[{\"id\":\"o7\"},{\"id\":\"o8\"}],\"total\":9}");
            } else if (pathAndQuery.startsWith("/cursor")) {
                boolean first = pathAndQuery.contains("cursor=start");
                respond(exchange, 200, first
                        ? "{\"items\":[{\"id\":\"c1\"}],\"nextCursor\":\"next\",\"hasMore\":true}"
                        : "{\"items\":[{\"id\":\"c2\"}],\"hasMore\":false}");
            } else if (pathAndQuery.equals("/next")) {
                respond(exchange, 200, "{\"items\":[{\"id\":\"n1\"}],\"next\":\"/next?page=2\"}");
            } else if (pathAndQuery.equals("/next?page=2")) {
                respond(exchange, 200, "{\"items\":[{\"id\":\"n2\"}],\"next\":null}");
            } else {
                respond(exchange, 404, "{}");
            }
        });
        GenericHttpApiConnector connector = connector();
        HttpApiContracts.RuntimeConnection connection = runtimeConnection();

        PullResult offset = connector.pull(new HttpApiContracts.PullRequest(connection, resource(
                request(HttpApiContracts.HttpMethod.GET, "/offset"),
                HttpApiContracts.InvocationType.PAGINATED_REQUEST,
                new HttpApiContracts.OffsetLimitPagination(
                        HttpApiContracts.ValueLocation.QUERY, "offset", "limit", 5, 2, null, "/total"), null), List.of()));
        PullResult cursor = connector.pull(new HttpApiContracts.PullRequest(connection, resource(
                request(HttpApiContracts.HttpMethod.GET, "/cursor"),
                HttpApiContracts.InvocationType.PAGINATED_REQUEST,
                new HttpApiContracts.CursorPagination(
                        HttpApiContracts.ValueLocation.QUERY, "cursor", "start", "/nextCursor", "/hasMore"), null), List.of()));
        PullResult next = connector.pull(new HttpApiContracts.PullRequest(connection, resource(
                request(HttpApiContracts.HttpMethod.GET, "/next"),
                HttpApiContracts.InvocationType.PAGINATED_REQUEST,
                new HttpApiContracts.NextUrlPagination("/next", true), null), List.of()));

        assertEquals(List.of(List.of("o5"), List.of("o6"), List.of("o7"), List.of("o8")), offset.rows());
        assertEquals(List.of(List.of("c1"), List.of("c2")), cursor.rows());
        assertEquals(List.of(List.of("n1"), List.of("n2")), next.rows());
        assertTrue(requests.containsAll(List.of(
                "/offset?offset=5&limit=2", "/offset?offset=7&limit=2",
                "/cursor?cursor=start", "/cursor?cursor=next", "/next", "/next?page=2")));
    }

    @Test
    void rejectsRepeatedCursorRepeatedNextUrlAndCrossOriginNextUrl() throws Exception {
        startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/cursor-loop".equals(path)) {
                respond(exchange, 200, "{\"items\":[{\"id\":\"x\"}],\"nextCursor\":\"same\"}");
            } else if ("/url-loop".equals(path)) {
                respond(exchange, 200, "{\"items\":[{\"id\":\"x\"}],\"next\":\"/url-loop\"}");
            } else {
                respond(exchange, 200,
                        "{\"items\":[{\"id\":\"x\"}],\"next\":\"http://example.invalid/exfiltrate\"}");
            }
        });
        HttpApiContracts.RuntimeConnection connection = runtimeConnection();

        assertFailureCode("API_PAGINATION_LOOP", () -> connector().pull(new HttpApiContracts.PullRequest(
                connection, resource(request(HttpApiContracts.HttpMethod.GET, "/cursor-loop"),
                HttpApiContracts.InvocationType.PAGINATED_REQUEST,
                new HttpApiContracts.CursorPagination(
                        HttpApiContracts.ValueLocation.QUERY, "cursor", null, "/nextCursor", null), null), List.of())));
        assertFailureCode("API_PAGINATION_LOOP", () -> connector().pull(new HttpApiContracts.PullRequest(
                connection, resource(request(HttpApiContracts.HttpMethod.GET, "/url-loop"),
                HttpApiContracts.InvocationType.PAGINATED_REQUEST,
                new HttpApiContracts.NextUrlPagination("/next", true), null), List.of())));
        assertFailureCode("API_NEXT_URL_ORIGIN_REJECTED", () -> connector().pull(new HttpApiContracts.PullRequest(
                connection, resource(request(HttpApiContracts.HttpMethod.GET, "/cross-origin"),
                HttpApiContracts.InvocationType.PAGINATED_REQUEST,
                new HttpApiContracts.NextUrlPagination("/next", true), null), List.of())));
    }

    @Test
    void enforcesPageRowByteAndDurationLimits() throws Exception {
        startServer(exchange -> {
            switch (exchange.getRequestURI().getPath()) {
                case "/pages" -> respond(exchange, 200,
                        "{\"items\":[{\"id\":\"x\"}],\"hasMore\":true}");
                case "/rows" -> respond(exchange, 200,
                        "{\"items\":[{\"id\":\"x\"},{\"id\":\"y\"}]}");
                case "/bytes" -> respond(exchange, 200,
                        "{\"items\":[{\"id\":\"response-is-larger-than-limit\"}]}");
                default -> respond(exchange, 200, "{\"items\":[]}");
            }
        });
        HttpApiContracts.RuntimeConnection connection = runtimeConnection();

        assertFailureCode("API_MAX_PAGES_EXCEEDED", () -> connector().pull(new HttpApiContracts.PullRequest(
                connection, resource(request(HttpApiContracts.HttpMethod.GET, "/pages"),
                HttpApiContracts.SigningConfiguration.none(), HttpApiContracts.InvocationType.PAGINATED_REQUEST,
                new HttpApiContracts.PageNumberPagination(
                        HttpApiContracts.ValueLocation.QUERY, "page", "size", 1, 1, "/hasMore", null),
                null, "/items", defaultFields(), new HttpApiContracts.ExecutionLimits(1, 100, 1024, 30)), List.of())));
        assertFailureCode("API_MAX_ROWS_EXCEEDED", () -> connector().pull(new HttpApiContracts.PullRequest(
                connection, resource(request(HttpApiContracts.HttpMethod.GET, "/rows"),
                HttpApiContracts.SigningConfiguration.none(), HttpApiContracts.InvocationType.SINGLE_REQUEST,
                new HttpApiContracts.NoPagination(), null, "/items", defaultFields(),
                new HttpApiContracts.ExecutionLimits(2, 1, 1024, 30)), List.of())));
        assertFailureCode("API_MAX_RESPONSE_BYTES_EXCEEDED", () -> connector().pull(new HttpApiContracts.PullRequest(
                connection, resource(request(HttpApiContracts.HttpMethod.GET, "/bytes"),
                HttpApiContracts.SigningConfiguration.none(), HttpApiContracts.InvocationType.SINGLE_REQUEST,
                new HttpApiContracts.NoPagination(), null, "/items", defaultFields(),
                new HttpApiContracts.ExecutionLimits(2, 10, 16, 30)), List.of())));
        assertFailureCode("API_MAX_DURATION_EXCEEDED", () -> connector().pull(new HttpApiContracts.PullRequest(
                connection, resource(request(HttpApiContracts.HttpMethod.GET, "/duration"),
                HttpApiContracts.SigningConfiguration.none(), HttpApiContracts.InvocationType.SINGLE_REQUEST,
                new HttpApiContracts.NoPagination(), null, "/items", defaultFields(),
                new HttpApiContracts.ExecutionLimits(2, 10, 1024, 0)), List.of())));
    }

    @Test
    void redactsDynamicTokenAndSignatureFromErrorPreview() throws Exception {
        String signingSecret = "hmac-secret-value";
        startServer(exchange -> {
            if ("/oauth/token".equals(exchange.getRequestURI().getPath())) {
                respond(exchange, 200, "{\"access_token\":\"runtime-token-secret\",\"expires_in\":3600}");
                return;
            }
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String signature = exchange.getRequestHeaders().getFirst("X-Signature");
            respond(exchange, 500, "{\"authorization\":\"" + authorization
                    + "\",\"signature\":\"" + signature + "\",\"secret\":\"" + signingSecret + "\"}");
        });
        HttpApiContracts.RuntimeConnection connection = runtimeConnection(
                new HttpApiContracts.OAuth2ClientCredentialsAuthentication(
                        baseUrl() + "/oauth/token", "client", List.of(), null,
                        HttpApiContracts.ValueLocation.HEADER, "Authorization", "Bearer ${token}", true),
                new HttpApiContracts.CredentialBundle(null, null, null, "client-secret", null,
                        signingSecret, null), 0);
        HttpApiContracts.SigningConfiguration signing = new HttpApiContracts.SigningConfiguration(
                HttpApiContracts.SignatureType.HMAC_SHA256, "${request.path}|${token}", null, null,
                new HttpApiContracts.SignatureOutput(
                        "X-Signature", HttpApiContracts.ValueLocation.HEADER,
                        HttpApiContracts.SignatureEncoding.HEX_LOWERCASE, null));
        HttpApiContracts.ResourceDefinition resource = resource(
                request(HttpApiContracts.HttpMethod.GET, "/items"), signing,
                HttpApiContracts.InvocationType.SINGLE_REQUEST, new HttpApiContracts.NoPagination(),
                null, "/items", defaultFields(), defaultLimits());

        HttpApiExecutionException failure = assertThrows(HttpApiExecutionException.class, () -> connector().pull(
                new HttpApiContracts.PullRequest(connection, resource, List.of())));

        assertEquals("API_HTTP_ERROR", failure.code());
        assertTrue(failure.responsePreview().contains("[REDACTED]"));
        assertFalse(failure.responsePreview().contains("runtime-token-secret"));
        assertFalse(failure.responsePreview().contains(signingSecret));
        assertFalse(failure.responsePreview().contains(hmacHex(
                signingSecret, "/items|runtime-token-secret")));
    }

    private GenericHttpApiConnector connector() {
        return new GenericHttpApiConnector(new ObjectMapper());
    }

    private HttpApiContracts.RuntimeConnection runtimeConnection() {
        return runtimeConnection(
                new HttpApiContracts.NoneAuthentication(),
                new HttpApiContracts.CredentialBundle(null, null, null, null, null, null, null), 0);
    }

    private HttpApiContracts.RuntimeConnection runtimeConnection(
            HttpApiContracts.AuthenticationConfiguration authentication,
            HttpApiContracts.CredentialBundle credentials,
            int maxRetries
    ) {
        return new HttpApiContracts.RuntimeConnection(
                new HttpApiContracts.ConnectionConfiguration(
                        baseUrl(), List.of(), 2_000, 5_000, 0, maxRetries,
                        authentication, credentials.signingSecret() != null, credentials.signingPrivateKey() != null),
                credentials
        );
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static HttpApiContracts.RequestTemplate request(HttpApiContracts.HttpMethod method, String path) {
        return new HttpApiContracts.RequestTemplate(method, path, List.of(), List.of(),
                method == HttpApiContracts.HttpMethod.POST ? "{}" : null);
    }

    private static HttpApiContracts.ResourceDefinition resource(
            HttpApiContracts.RequestTemplate request,
            HttpApiContracts.InvocationType invocationType,
            HttpApiContracts.PaginationConfiguration pagination,
            HttpApiContracts.AsyncJobConfiguration async
    ) {
        return resource(request, HttpApiContracts.SigningConfiguration.none(), invocationType,
                pagination, async, "/items", defaultFields(), defaultLimits());
    }

    private static HttpApiContracts.ResourceDefinition resource(
            HttpApiContracts.RequestTemplate request,
            HttpApiContracts.SigningConfiguration signing,
            HttpApiContracts.InvocationType invocationType,
            HttpApiContracts.PaginationConfiguration pagination,
            HttpApiContracts.AsyncJobConfiguration async,
            String recordsPointer,
            List<HttpApiContracts.OutputField> fields,
            HttpApiContracts.ExecutionLimits limits
    ) {
        return new HttpApiContracts.ResourceDefinition(
                UUID.randomUUID(), UUID.randomUUID(), "orders", "Orders",
                HttpApiContracts.GENERIC_CONNECTOR, true, request,
                signing, invocationType, pagination, async, recordsPointer, fields, limits
        );
    }

    private static List<HttpApiContracts.OutputField> defaultFields() {
        return List.of(new HttpApiContracts.OutputField(
                "id", "/id", PlatformTypeDefinition.of(PlatformDataType.STRING), false, null));
    }

    private static HttpApiContracts.ExecutionLimits defaultLimits() {
        return new HttpApiContracts.ExecutionLimits(10, 100, 1024 * 1024, 30);
    }

    private static HttpApiContracts.ResourceDefinition asyncResource(String prefix, int timeoutMs) {
        HttpApiContracts.RequestTemplate submission = new HttpApiContracts.RequestTemplate(
                HttpApiContracts.HttpMethod.POST, "/" + prefix + "/submit", List.of(), List.of(), "{}");
        HttpApiContracts.AsyncJobConfiguration async = new HttpApiContracts.AsyncJobConfiguration(
                request(HttpApiContracts.HttpMethod.GET, "/" + prefix + "/${jobId}/status"),
                "/jobId", "/status", Set.of("RUNNING"), Set.of("SUCCESS"), Set.of("FAILED"),
                100, timeoutMs,
                request(HttpApiContracts.HttpMethod.GET, "/" + prefix + "/${jobId}/result"));
        return resource(submission, HttpApiContracts.SigningConfiguration.none(),
                HttpApiContracts.InvocationType.ASYNC_JOB, new HttpApiContracts.NoPagination(), async,
                "/items", defaultFields(), defaultLimits());
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Throwable failure) {
                respond(exchange, 500, "{\"error\":\"test handler failed\"}");
                if (failure instanceof RuntimeException runtime) throw runtime;
                if (failure instanceof Error error) throw error;
                throw new IOException(failure);
            } finally {
                exchange.close();
            }
        });
        server.start();
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
        return hex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String sha256Hex(String value) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private static void assertFailureCode(String code, org.junit.jupiter.api.function.Executable executable) {
        assertEquals(code, assertThrows(HttpApiExecutionException.class, executable).code());
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
