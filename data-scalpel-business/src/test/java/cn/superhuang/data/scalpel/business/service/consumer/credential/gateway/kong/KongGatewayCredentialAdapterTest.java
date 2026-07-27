package cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.kong;

import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialOperationException;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialInspectionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialReference;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialResult;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialSpec;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.ServiceGatewayProperties;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KongGatewayCredentialAdapterTest {

    private HttpServer server;
    private KongGatewayCredentialAdapter adapter;
    private final List<RecordedRequest> requests = new ArrayList<>();
    private ExchangeHandler handler;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
            ));
            handler.handle(exchange);
        });
        server.start();
        String baseUrl = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        adapter = new KongGatewayCredentialAdapter(properties(baseUrl));
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void createsOwnedApiKeyAndNeverAddsAdminAuthentication() {
        GatewayCredentialSpec spec = spec();
        handler = exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "{\"data\":[]}");
            } else {
                respond(exchange, 201, credential("kong-key-1", spec));
            }
        };

        GatewayCredentialResult result = adapter.upsert(spec);

        assertEquals("kong-key-1", result.externalId());
        assertEquals(List.of("GET", "POST"), requests.stream().map(RecordedRequest::method).toList());
        assertEquals("/consumers/kong-consumer-1/key-auth", requests.get(1).path());
        assertTrue(requests.get(1).body().contains("\"key\":\"dsk_secret\""));
        assertTrue(requests.get(1).body().contains("\"datascalpel-credential-" + spec.id() + "\""));
        assertTrue(requests.stream().allMatch(request -> request.authorization() == null));
    }

    @Test
    void updatesOwnedCredentialAndRecoversConcurrentCreateConflict() {
        GatewayCredentialSpec spec = spec();
        AtomicInteger listRequests = new AtomicInteger();
        handler = exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                if (listRequests.getAndIncrement() == 0) {
                    respond(exchange, 200, "{\"data\":[]}");
                } else {
                    respond(exchange, 200, "{\"data\":[" + credential("kong-key-race", spec) + "]}");
                }
            } else if ("POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 409, "{\"message\":\"unique constraint\"}");
            } else {
                respond(exchange, 200, credential("kong-key-race", spec));
            }
        };

        GatewayCredentialResult created = adapter.upsert(spec);
        assertEquals("kong-key-race", created.externalId());

        requests.clear();
        handler = exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "{\"data\":[" + credential("kong-key-race", spec) + "]}");
            } else {
                respond(exchange, 200, credential("kong-key-race", spec));
            }
        };
        GatewayCredentialResult updated = adapter.upsert(spec);

        assertEquals("kong-key-race", updated.externalId());
        assertEquals(List.of("GET", "PATCH"), requests.stream().map(RecordedRequest::method).toList());
        assertEquals("/consumers/kong-consumer-1/key-auth/kong-key-race", requests.get(1).path());
    }

    @Test
    void refusesForeignCredentialsAndDeletesOwnedCredentialIdempotently() {
        GatewayCredentialSpec spec = spec();
        handler = exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, """
                        {"data":[{"id":"foreign","key":"hidden","consumer":{"id":"kong-consumer-1"},"tags":["other"]}]}
                        """);
            } else {
                respond(exchange, 409, "{\"message\":\"unique constraint\"}");
            }
        };
        GatewayCredentialOperationException conflict = assertThrows(
                GatewayCredentialOperationException.class,
                () -> adapter.upsert(spec)
        );
        assertTrue(conflict.getMessage().contains("同步后无法读取"));

        requests.clear();
        handler = exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, credential("kong-key-1", spec));
            } else {
                respond(exchange, 204, "");
            }
        };
        adapter.remove(reference("kong-key-1"));
        assertEquals(List.of("GET", "DELETE"), requests.stream().map(RecordedRequest::method).toList());

        requests.clear();
        handler = exchange -> respond(exchange, 404, "");
        adapter.remove(reference("missing"));
        assertEquals(1, requests.size());
        assertFalse(requests.stream().anyMatch(request -> "DELETE".equals(request.method())));
    }

    @Test
    void inspectsApiKeyDigestAndRequiresRotationForSecretDriftOrMissingRemoteKey() {
        GatewayCredentialSpec spec = spec();
        handler = exchange -> respond(exchange, 200, credentialWithKey("kong-key-1", spec, spec.secret()));
        GatewayInspectionResult synchronizedResult = adapter.inspect(new GatewayCredentialInspectionSpec(
                spec.id(),
                spec.consumerId(),
                spec.consumerCode(),
                spec.consumerExternalId(),
                "kong-key-1",
                digest(spec.secret()),
                true
        ));
        assertEquals(GatewayReconciliationStatus.IN_SYNC, synchronizedResult.status());

        requests.clear();
        handler = exchange -> respond(exchange, 200, credentialWithKey("kong-key-1", spec, "changed"));
        GatewayInspectionResult secretMismatch = adapter.inspect(new GatewayCredentialInspectionSpec(
                spec.id(),
                spec.consumerId(),
                spec.consumerCode(),
                spec.consumerExternalId(),
                "kong-key-1",
                digest(spec.secret()),
                true
        ));
        assertEquals(GatewayReconciliationReason.SECRET_MISMATCH, secretMismatch.reason());

        requests.clear();
        handler = exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/missing")) {
                respond(exchange, 404, "");
            } else {
                respond(exchange, 200, "{\"data\":[]}");
            }
        };
        GatewayInspectionResult missing = adapter.inspect(new GatewayCredentialInspectionSpec(
                spec.id(),
                spec.consumerId(),
                spec.consumerCode(),
                spec.consumerExternalId(),
                "missing",
                digest(spec.secret()),
                true
        ));
        assertEquals(GatewayReconciliationReason.REMOTE_MISSING, missing.reason());
        assertTrue(missing.message().contains("轮换"));
        assertTrue(requests.stream().allMatch(request -> "GET".equals(request.method())));
    }

    private static ServiceGatewayProperties properties(String adminUrl) {
        return new ServiceGatewayProperties(
                GatewayProvider.KONG,
                new ServiceGatewayProperties.Kong(
                        adminUrl,
                        "http://gateway.test:8000",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2)
                )
        );
    }

    private static GatewayCredentialSpec spec() {
        return new GatewayCredentialSpec(
                UUID.fromString("ff3ada0e-8c8b-449a-9074-2631a7ef7aa0"),
                UUID.fromString("0d89de63-25b5-44f6-b474-aee1f0eea410"),
                "client-a",
                "kong-consumer-1",
                "dsk_secret",
                2
        );
    }

    private static GatewayCredentialReference reference(String externalId) {
        GatewayCredentialSpec spec = spec();
        return new GatewayCredentialReference(
                spec.id(),
                spec.consumerId(),
                spec.consumerCode(),
                spec.consumerExternalId(),
                externalId
        );
    }

    private static String credential(String externalId, GatewayCredentialSpec spec) {
        return credentialWithKey(externalId, spec, "hidden");
    }

    private static String credentialWithKey(
            String externalId,
            GatewayCredentialSpec spec,
            String key
    ) {
        return """
                {
                  "id":"%s",
                  "key":"%s",
                  "consumer":{"id":"%s"},
                  "tags":["datascalpel","datascalpel-credential","datascalpel-credential-%s"]
                }
                """.formatted(externalId, key, spec.consumerExternalId(), spec.id());
    }

    private static String digest(String secret) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (!body.isEmpty()) exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private record RecordedRequest(
            String method,
            String path,
            String query,
            String authorization,
            String body
    ) {
    }
}
