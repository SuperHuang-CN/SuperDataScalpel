package cn.superhuang.data.scalpel.business.service.consumer.gateway.kong;

import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerOperationException;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerInspectionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerReference;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerResult;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerSpec;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KongGatewayConsumerAdapterTest {

    private HttpServer server;
    private KongGatewayConsumerAdapter adapter;
    private final List<RecordedRequest> requests = new ArrayList<>();
    private ExchangeHandler handler;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/consumers", exchange -> {
            requests.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
            ));
            handler.handle(exchange);
        });
        server.start();
        String adminUrl = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        adapter = new KongGatewayConsumerAdapter(new ServiceGatewayProperties(
                GatewayProvider.KONG,
                new ServiceGatewayProperties.Kong(
                        adminUrl,
                        null,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2)
                )
        ));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void createsMissingConsumerWithStableOwnershipAndNoAuthenticationHeader() {
        GatewayConsumerSpec consumer = consumer();
        handler = exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 404, "");
                return;
            }
            respond(exchange, 201, kongConsumer("kong-1", consumer));
        };

        GatewayConsumerResult result = adapter.upsert(consumer);

        assertEquals("kong-1", result.externalId());
        assertEquals(List.of("GET", "POST"), requests.stream().map(RecordedRequest::method).toList());
        assertTrue(requests.get(1).body().contains("\"username\":\"customer.api\""));
        assertTrue(requests.get(1).body().contains("\"custom_id\":\"" + consumer.id() + "\""));
        assertTrue(requests.get(1).body().contains("\"datascalpel-consumer-" + consumer.id() + "\""));
        assertTrue(requests.stream().allMatch(request -> request.authorization() == null));
    }

    @Test
    void updatesOwnedConsumerAndPreservesExistingTags() {
        GatewayConsumerSpec consumer = consumer();
        handler = exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, """
                        {"id":"kong-1","username":"customer.api","custom_id":"%s","tags":["existing"]}
                        """.formatted(consumer.id()));
                return;
            }
            respond(exchange, 200, """
                    {"id":"kong-1","username":"customer.api","custom_id":"%s","tags":["existing","datascalpel"]}
                    """.formatted(consumer.id()));
        };

        GatewayConsumerResult result = adapter.upsert(consumer);

        assertEquals("kong-1", result.externalId());
        assertEquals(List.of("GET", "PATCH"), requests.stream().map(RecordedRequest::method).toList());
        assertEquals("/consumers/kong-1", requests.get(1).path());
        assertTrue(requests.get(1).body().contains("\"existing\""));
    }

    @Test
    void rejectsTakingOverConsumerWithSameUsernameButDifferentOwner() {
        handler = exchange -> respond(exchange, 200, """
                {"id":"foreign","username":"customer.api","custom_id":"another-system","tags":[]}
                """);

        GatewayConsumerOperationException exception = assertThrows(
                GatewayConsumerOperationException.class,
                () -> adapter.upsert(consumer())
        );

        assertTrue(exception.getMessage().contains("拒绝接管"));
        assertEquals(1, requests.size());
    }

    @Test
    void resolvesConcurrentCreateConflictByReloadingOwnedConsumer() {
        GatewayConsumerSpec consumer = consumer();
        AtomicInteger getCount = new AtomicInteger();
        handler = exchange -> {
            if ("GET".equals(exchange.getRequestMethod()) && getCount.getAndIncrement() == 0) {
                respond(exchange, 404, "");
            } else if ("POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 409, "{\"message\":\"unique constraint\"}");
            } else {
                respond(exchange, 200, kongConsumer("kong-raced", consumer));
            }
        };

        GatewayConsumerResult result = adapter.upsert(consumer);

        assertEquals("kong-raced", result.externalId());
        assertEquals(List.of("GET", "POST", "GET", "PATCH"),
                requests.stream().map(RecordedRequest::method).toList());
    }

    @Test
    void deletesOnlyOwnedConsumerAndTreatsMissingConsumerAsSuccess() {
        GatewayConsumerSpec consumer = consumer();
        handler = exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, kongConsumer("kong-1", consumer));
            } else {
                respond(exchange, 204, "");
            }
        };

        adapter.remove(new GatewayConsumerReference(consumer.id(), consumer.code(), "kong-1"));

        assertEquals(List.of("GET", "DELETE"), requests.stream().map(RecordedRequest::method).toList());
        assertEquals("/consumers/kong-1", requests.get(0).path());
        assertEquals("/consumers/kong-1", requests.get(1).path());

        requests.clear();
        handler = exchange -> respond(exchange, 404, "");
        adapter.remove(new GatewayConsumerReference(consumer.id(), consumer.code(), "kong-missing"));
        assertEquals(1, requests.size());
    }

    @Test
    void refusesToDeleteForeignConsumer() {
        GatewayConsumerSpec consumer = consumer();
        handler = exchange -> respond(exchange, 200, """
                {"id":"kong-1","username":"customer.api","custom_id":"foreign","tags":[]}
                """);

        GatewayConsumerOperationException exception = assertThrows(
                GatewayConsumerOperationException.class,
                () -> adapter.remove(new GatewayConsumerReference(consumer.id(), consumer.code(), "kong-1"))
        );

        assertTrue(exception.getMessage().contains("归属校验失败"));
        assertEquals(1, requests.size());
        assertFalse(requests.stream().anyMatch(request -> "DELETE".equals(request.method())));
    }

    @Test
    void exposesBoundedSafeKongErrorWithoutLeakingUnknownResponseFields() {
        handler = exchange -> respond(exchange, 500, """
                {"message":"Kong unavailable","debugSecret":"must-not-leak","stack":"internal stack"}
                """);

        GatewayConsumerOperationException exception = assertThrows(
                GatewayConsumerOperationException.class,
                () -> adapter.upsert(consumer())
        );

        assertTrue(exception.getMessage().contains("HTTP 500"));
        assertTrue(exception.getMessage().contains("Kong unavailable"));
        assertFalse(exception.getMessage().contains("must-not-leak"));
        assertFalse(exception.getMessage().contains("internal stack"));
    }

    @Test
    void inspectsConsumerWithoutMutatingKongAndReportsMissingOrOwnershipDrift() {
        GatewayConsumerSpec consumer = consumer();
        handler = exchange -> respond(exchange, 200, """
                {
                  "id":"kong-1",
                  "username":"customer.api",
                  "custom_id":"%s",
                  "tags":["datascalpel","datascalpel-consumer","datascalpel-consumer-%s"]
                }
                """.formatted(consumer.id(), consumer.id()));

        GatewayInspectionResult synchronizedResult = adapter.inspect(new GatewayConsumerInspectionSpec(
                consumer,
                new GatewayConsumerReference(consumer.id(), consumer.code(), "kong-1"),
                true
        ));

        assertEquals(GatewayReconciliationStatus.IN_SYNC, synchronizedResult.status());
        assertEquals(List.of("GET"), requests.stream().map(RecordedRequest::method).toList());

        requests.clear();
        handler = exchange -> respond(exchange, 404, "");
        GatewayInspectionResult missing = adapter.inspect(new GatewayConsumerInspectionSpec(
                consumer,
                new GatewayConsumerReference(consumer.id(), consumer.code(), "missing"),
                true
        ));
        assertEquals(GatewayReconciliationReason.REMOTE_MISSING, missing.reason());

        requests.clear();
        handler = exchange -> respond(exchange, 200, """
                {"id":"foreign","username":"customer.api","custom_id":"foreign","tags":[]}
                """);
        GatewayInspectionResult foreign = adapter.inspect(new GatewayConsumerInspectionSpec(
                consumer,
                new GatewayConsumerReference(consumer.id(), consumer.code(), "foreign"),
                true
        ));
        assertEquals(GatewayReconciliationReason.OWNER_MISMATCH, foreign.reason());
        assertTrue(requests.stream().allMatch(request -> "GET".equals(request.method())));
    }

    private static GatewayConsumerSpec consumer() {
        return new GatewayConsumerSpec(
                UUID.fromString("0f20aab0-3f4c-4b2a-8e32-3a0cdcff25f7"),
                "customer.api",
                "客户系统",
                "查询数据服务",
                1
        );
    }

    private static String kongConsumer(String externalId, GatewayConsumerSpec consumer) {
        return """
                {"id":"%s","username":"%s","custom_id":"%s","tags":["datascalpel"]}
                """.formatted(externalId, consumer.code(), consumer.id());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (!body.isEmpty()) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
        }
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private record RecordedRequest(String method, String path, String authorization, String body) {
    }
}
