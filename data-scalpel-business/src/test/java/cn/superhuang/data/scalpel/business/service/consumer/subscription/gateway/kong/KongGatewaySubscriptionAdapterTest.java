package cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.kong;

import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionOperationException;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionInspectionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionReference;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionResult;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionSpec;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.ServiceGatewayProperties;
import cn.superhuang.data.scalpel.business.service.gateway.kong.KongGatewayManagedNames;
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

class KongGatewaySubscriptionAdapterTest {

    private HttpServer server;
    private KongGatewaySubscriptionAdapter adapter;
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
        adapter = new KongGatewaySubscriptionAdapter(properties(baseUrl));
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void grantsOwnedMembershipWithStableKongGroupAndTags() {
        GatewaySubscriptionSpec spec = spec();
        handler = exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "{\"data\":[]}");
            } else {
                respond(exchange, 201, membership("membership-1", spec));
            }
        };

        GatewaySubscriptionResult result = adapter.grant(spec);

        assertEquals("membership-1", result.externalMembershipId());
        assertEquals(List.of("GET", "POST"), requests.stream().map(RecordedRequest::method).toList());
        assertTrue(requests.get(0).query().contains("tags="));
        assertTrue(requests.get(1).body().contains(
                "\"group\":\"" + KongGatewayManagedNames.serviceAclGroup(spec.dataServiceId()) + "\""
        ));
        assertTrue(requests.get(1).body().contains("\"datascalpel-subscription-" + spec.id() + "\""));
        assertTrue(requests.stream().allMatch(request -> request.authorization() == null));
    }

    @Test
    void followsPaginationAndRecoversConcurrentGrantByOwnerTag() {
        GatewaySubscriptionSpec spec = spec();
        AtomicInteger getCount = new AtomicInteger();
        handler = exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                if (getCount.getAndIncrement() == 0) {
                    respond(exchange, 200, """
                            {"data":[{"id":"other","group":"other","consumer":{"id":"kong-consumer-1"},"tags":[]}],
                             "offset":"next-page"}
                            """);
                } else {
                    respond(exchange, 200, "{\"data\":[" + membership("membership-page-2", spec) + "]}");
                }
            } else {
                respond(exchange, 500, "{\"message\":\"should not create\"}");
            }
        };

        GatewaySubscriptionResult paged = adapter.grant(spec);
        assertEquals("membership-page-2", paged.externalMembershipId());
        assertTrue(requests.get(1).query().contains("offset=next-page"));

        requests.clear();
        getCount.set(0);
        handler = exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                if (getCount.getAndIncrement() == 0) {
                    respond(exchange, 200, "{\"data\":[]}");
                } else {
                    respond(exchange, 200, "{\"data\":[" + membership("membership-race", spec) + "]}");
                }
            } else {
                respond(exchange, 409, "{\"message\":\"unique constraint\"}");
            }
        };

        GatewaySubscriptionResult race = adapter.grant(spec);
        assertEquals("membership-race", race.externalMembershipId());
        assertEquals(List.of("GET", "POST", "GET"), requests.stream().map(RecordedRequest::method).toList());
    }

    @Test
    void refusesForeignMembershipAndRevokesOwnedMembershipIdempotently() {
        GatewaySubscriptionSpec spec = spec();
        AtomicInteger getCount = new AtomicInteger();
        String group = KongGatewayManagedNames.serviceAclGroup(spec.dataServiceId());
        handler = exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 409, "{\"message\":\"unique constraint\"}");
            } else if (getCount.getAndIncrement() == 0) {
                respond(exchange, 200, "{\"data\":[]}");
            } else if (getCount.get() == 2) {
                respond(exchange, 200, "{\"data\":[]}");
            } else {
                respond(exchange, 200, """
                        {"data":[{"id":"foreign","group":"%s","consumer":{"id":"kong-consumer-1"},"tags":["other"]}]}
                        """.formatted(group));
            }
        };

        GatewaySubscriptionOperationException conflict = assertThrows(
                GatewaySubscriptionOperationException.class,
                () -> adapter.grant(spec)
        );
        assertTrue(conflict.getMessage().contains("归属校验失败"));

        requests.clear();
        handler = exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, membership("membership-1", spec));
            } else {
                respond(exchange, 204, "");
            }
        };
        adapter.revoke(reference("membership-1"));
        assertEquals(List.of("GET", "DELETE"), requests.stream().map(RecordedRequest::method).toList());

        requests.clear();
        handler = exchange -> respond(exchange, 404, "");
        adapter.revoke(reference("missing"));
        assertEquals(1, requests.size());
        assertFalse(requests.stream().anyMatch(request -> "DELETE".equals(request.method())));
    }

    @Test
    void inspectsSubscriptionPresenceWithoutMutatingKong() {
        GatewaySubscriptionSpec spec = spec();
        handler = exchange -> respond(exchange, 200, membership("membership-1", spec));

        GatewayInspectionResult synchronizedResult = adapter.inspect(
                new GatewaySubscriptionInspectionSpec(spec, "membership-1", true)
        );
        assertEquals(GatewayReconciliationStatus.IN_SYNC, synchronizedResult.status());

        requests.clear();
        handler = exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/missing")) {
                respond(exchange, 404, "");
            } else {
                respond(exchange, 200, "{\"data\":[]}");
            }
        };
        GatewayInspectionResult revoked = adapter.inspect(
                new GatewaySubscriptionInspectionSpec(spec, "missing", false)
        );
        assertEquals(GatewayReconciliationStatus.IN_SYNC, revoked.status());

        requests.clear();
        handler = exchange -> respond(exchange, 200, membership("membership-1", spec));
        GatewayInspectionResult unexpected = adapter.inspect(
                new GatewaySubscriptionInspectionSpec(spec, "membership-1", false)
        );
        assertEquals(GatewayReconciliationReason.UNEXPECTED_REMOTE, unexpected.reason());
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

    private static GatewaySubscriptionSpec spec() {
        return new GatewaySubscriptionSpec(
                UUID.fromString("0bd885e4-e796-4a35-8e20-af497ef4d27b"),
                UUID.fromString("7cfdc8c5-0c12-4565-b502-4196afdc03d1"),
                "client-a",
                "kong-consumer-1",
                UUID.fromString("6de591ba-cae3-45b5-bf76-cb254841cf9a"),
                "orders",
                "kong-service-1",
                "kong-route-1"
        );
    }

    private static GatewaySubscriptionReference reference(String externalMembershipId) {
        GatewaySubscriptionSpec spec = spec();
        return new GatewaySubscriptionReference(
                spec.id(),
                spec.consumerId(),
                spec.consumerCode(),
                spec.consumerExternalId(),
                spec.dataServiceId(),
                spec.dataServiceCode(),
                spec.serviceExternalId(),
                spec.routeExternalId(),
                externalMembershipId
        );
    }

    private static String membership(String externalId, GatewaySubscriptionSpec spec) {
        return """
                {
                  "id":"%s",
                  "group":"%s",
                  "consumer":{"id":"%s"},
                  "tags":["datascalpel","datascalpel-subscription","datascalpel-subscription-%s"]
                }
                """.formatted(
                externalId,
                KongGatewayManagedNames.serviceAclGroup(spec.dataServiceId()),
                spec.consumerExternalId(),
                spec.id()
        );
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
