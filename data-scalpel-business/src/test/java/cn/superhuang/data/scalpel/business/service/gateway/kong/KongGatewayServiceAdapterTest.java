package cn.superhuang.data.scalpel.business.service.gateway.kong;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.ServiceGatewayProperties;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceOperationException;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceInspectionSpec;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceReference;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceResult;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceSpec;
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
import java.net.URI;
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

class KongGatewayServiceAdapterTest {

    private HttpServer server;
    private KongGatewayServiceAdapter adapter;
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
        adapter = new KongGatewayServiceAdapter(new ServiceGatewayProperties(
                GatewayProvider.KONG,
                new ServiceGatewayProperties.Kong(
                        baseUrl,
                        "http://gateway.test:8000/",
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
    void createsOwnedServiceAndRouteWithoutAuthenticationHeader() {
        GatewayServiceSpec service = service();
        handler = exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod()) && path.endsWith("/plugins")) {
                respond(exchange, 200, "{\"data\":[]}");
            } else if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 404, "");
            } else if ("/services".equals(path)) {
                respond(exchange, 201, kongService("kong-service-1", service));
            } else {
                respond(exchange, 201, kongRoute("kong-route-1", "kong-service-1", service));
            }
        };

        GatewayServiceResult result = adapter.publish(service);

        assertEquals("kong-service-1", result.externalServiceId());
        assertEquals("kong-route-1", result.externalRouteId());
        assertEquals("http://gateway.test:8000/open-api/v1/orders", result.gatewayUrl());
        assertEquals(List.of("GET", "POST", "GET", "GET", "GET", "POST"),
                requests.stream().map(RecordedRequest::method).toList());
        assertEquals("/services/datascalpel-service-" + service.id(), requests.get(0).path());
        assertEquals("/services/kong-service-1/routes", requests.get(5).path());
        assertTrue(requests.get(1).body().contains("\"url\":\"http://engine.test:8081\""));
        assertTrue(requests.get(5).body().contains("\"paths\":[\"/open-api/v1/orders\"]"));
        assertTrue(requests.get(5).body().contains("\"strip_path\":false"));
        assertTrue(requests.get(5).body().contains("\"datascalpel-data-service-" + service.id() + "\""));
        assertTrue(requests.stream().allMatch(request -> request.authorization() == null));
    }

    @Test
    void updatesOwnedServiceAndRouteAndPreservesExistingTags() {
        GatewayServiceSpec service = service();
        handler = exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/services/") && "GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, kongService("kong-service-1", service, "existing"));
            } else if (path.startsWith("/services/") && "PATCH".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, kongService("kong-service-1", service, "existing"));
            } else if (path.startsWith("/routes/") && "GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, kongRoute("kong-route-1", "kong-service-1", service, "route-existing"));
            } else {
                respond(exchange, 200, kongRoute("kong-route-1", "kong-service-1", service, "route-existing"));
            }
        };

        GatewayServiceResult result = adapter.publish(service);

        assertEquals("kong-route-1", result.externalRouteId());
        assertEquals(List.of("GET", "PATCH", "GET", "GET", "GET", "PATCH"),
                requests.stream().map(RecordedRequest::method).toList());
        assertTrue(requests.get(1).body().contains("\"existing\""));
        assertTrue(requests.get(5).body().contains("\"route-existing\""));
    }

    @Test
    void resolvesConcurrentCreateConflictsByReloadingOwnedObjects() {
        GatewayServiceSpec service = service();
        AtomicInteger serviceGet = new AtomicInteger();
        AtomicInteger routeGet = new AtomicInteger();
        handler = exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/services/") && "GET".equals(exchange.getRequestMethod())) {
                if (serviceGet.getAndIncrement() == 0) respond(exchange, 404, "");
                else respond(exchange, 200, kongService("kong-service-race", service));
            } else if ("/services".equals(path)) {
                respond(exchange, 409, "{\"message\":\"unique constraint\"}");
            } else if (path.startsWith("/routes/") && "GET".equals(exchange.getRequestMethod())) {
                if (routeGet.getAndIncrement() == 0) respond(exchange, 404, "");
                else respond(exchange, 200, kongRoute("kong-route-race", "kong-service-race", service));
            } else if (path.endsWith("/routes") && "POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 409, "{\"message\":\"unique constraint\"}");
            } else if (path.startsWith("/services/")) {
                respond(exchange, 200, kongService("kong-service-race", service));
            } else {
                respond(exchange, 200, kongRoute("kong-route-race", "kong-service-race", service));
            }
        };

        GatewayServiceResult result = adapter.publish(service);

        assertEquals("kong-service-race", result.externalServiceId());
        assertEquals("kong-route-race", result.externalRouteId());
        assertTrue(requests.stream().anyMatch(request ->
                "POST".equals(request.method()) && "/services".equals(request.path())));
        assertTrue(requests.stream().anyMatch(request ->
                "POST".equals(request.method()) && request.path().endsWith("/routes")));
    }

    @Test
    void refusesToTakeOverForeignService() {
        GatewayServiceSpec service = service();
        handler = exchange -> respond(exchange, 200, """
                {"id":"foreign","name":"datascalpel-service-%s","tags":["other-system"]}
                """.formatted(service.id()));

        GatewayServiceOperationException exception = assertThrows(
                GatewayServiceOperationException.class,
                () -> adapter.publish(service)
        );

        assertTrue(exception.getMessage().contains("归属校验失败"));
        assertEquals(1, requests.size());
    }

    @Test
    void removesOwnedRouteBeforeServiceAndTreatsMissingObjectsAsSuccess() {
        GatewayServiceSpec service = service();
        handler = exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod()) && path.startsWith("/routes/")) {
                respond(exchange, 200, kongRoute("kong-route-1", "kong-service-1", service));
            } else if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, kongService("kong-service-1", service));
            } else {
                respond(exchange, 204, "");
            }
        };

        adapter.remove(new GatewayServiceReference(
                service.id(), service.code(), "kong-service-1", "kong-route-1"
        ));

        assertEquals(List.of(
                "GET /routes/kong-route-1",
                "DELETE /routes/kong-route-1",
                "GET /services/kong-service-1",
                "DELETE /services/kong-service-1"
        ), requests.stream().map(request -> request.method() + " " + request.path()).toList());

        requests.clear();
        handler = exchange -> respond(exchange, 404, "");
        adapter.remove(new GatewayServiceReference(
                service.id(), service.code(), "missing-service", "missing-route"
        ));
        assertEquals(List.of("GET /routes/missing-route", "GET /services/missing-service"),
                requests.stream().map(request -> request.method() + " " + request.path()).toList());
    }

    @Test
    void refusesToDeleteForeignRoute() {
        GatewayServiceSpec service = service();
        handler = exchange -> respond(exchange, 200, """
                {
                  "id":"kong-route-1",
                  "name":"datascalpel-route-%s",
                  "paths":["/open-api/v1/orders"],
                  "methods":["POST"],
                  "protocols":["http","https"],
                  "strip_path":false,
                  "preserve_host":false,
                  "service":{"id":"kong-service-1"},
                  "tags":["other-system"]
                }
                """.formatted(service.id()));

        GatewayServiceOperationException exception = assertThrows(
                GatewayServiceOperationException.class,
                () -> adapter.remove(new GatewayServiceReference(
                        service.id(), service.code(), "kong-service-1", "kong-route-1"
                ))
        );

        assertTrue(exception.getMessage().contains("归属校验失败"));
        assertFalse(requests.stream().anyMatch(request -> "DELETE".equals(request.method())));
    }

    @Test
    void exposesBoundedSafeKongErrorWithoutUnknownFields() {
        handler = exchange -> respond(exchange, 500, """
                {"message":"Kong unavailable","debugSecret":"must-not-leak","stack":"internal stack"}
                """);

        GatewayServiceOperationException exception = assertThrows(
                GatewayServiceOperationException.class,
                () -> adapter.publish(service())
        );

        assertTrue(exception.getMessage().contains("HTTP 500"));
        assertTrue(exception.getMessage().contains("Kong unavailable"));
        assertFalse(exception.getMessage().contains("must-not-leak"));
        assertFalse(exception.getMessage().contains("internal stack"));
    }

    @Test
    void publishesProtectedServiceOnlyAfterKeyAuthAndAclAreVerified() {
        GatewayServiceSpec service = protectedService();
        handler = exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod()) && path.startsWith("/services/datascalpel-service-")) {
                respond(exchange, 404, "");
            } else if ("POST".equals(exchange.getRequestMethod()) && "/services".equals(path)) {
                respond(exchange, 201, kongService("kong-service-1", service));
            } else if ("GET".equals(exchange.getRequestMethod()) && path.startsWith("/routes/")) {
                respond(exchange, 404, "");
            } else if ("GET".equals(exchange.getRequestMethod()) && path.endsWith("/plugins")) {
                if ("name=acl".equals(exchange.getRequestURI().getRawQuery())) {
                    // Kong 3.9 may return other Service plugins even when name is supplied.
                    respond(exchange, 200, "{\"data\":[" + keyAuthPlugin(service) + "]}");
                } else {
                    respond(exchange, 200, "{\"data\":[]}");
                }
            } else if ("POST".equals(exchange.getRequestMethod()) && path.endsWith("/plugins")) {
                if (requests.getLast().body().contains("\"name\":\"key-auth\"")) {
                    respond(exchange, 201, keyAuthPlugin(service));
                } else {
                    respond(exchange, 201, aclPlugin(service));
                }
            } else {
                respond(exchange, 201, kongRoute("kong-route-1", "kong-service-1", service));
            }
        };

        GatewayServiceResult result = adapter.publish(service);

        assertEquals("kong-route-1", result.externalRouteId());
        List<String> operations = requests.stream()
                .map(request -> request.method() + " " + request.path())
                .toList();
        int keyAuthCreate = indexOfBody("\"name\":\"key-auth\"");
        int aclCreate = indexOfBody("\"name\":\"acl\"");
        int routeCreate = operations.lastIndexOf("POST /services/kong-service-1/routes");
        assertTrue(keyAuthCreate > 0);
        assertTrue(aclCreate > keyAuthCreate);
        assertTrue(routeCreate > aclCreate);
        assertTrue(requests.get(keyAuthCreate).body().contains("\"key_names\":[\"X-API-Key\"]"));
        assertTrue(requests.get(keyAuthCreate).body().contains("\"key_in_query\":false"));
        assertTrue(requests.get(aclCreate).body().contains(
                "\"allow\":[\"" + KongGatewayManagedNames.serviceAclGroup(service.id()) + "\"]"
        ));
    }

    @Test
    void doesNotCreateProtectedRouteWhenPluginConfigurationFails() {
        GatewayServiceSpec service = protectedService();
        handler = exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod()) && path.startsWith("/services/datascalpel-service-")) {
                respond(exchange, 404, "");
            } else if ("POST".equals(exchange.getRequestMethod()) && "/services".equals(path)) {
                respond(exchange, 201, kongService("kong-service-1", service));
            } else if ("GET".equals(exchange.getRequestMethod()) && path.startsWith("/routes/")) {
                respond(exchange, 404, "");
            } else if ("GET".equals(exchange.getRequestMethod()) && path.endsWith("/plugins")) {
                respond(exchange, 200, "{\"data\":[]}");
            } else {
                respond(exchange, 500, "{\"message\":\"plugin unavailable\"}");
            }
        };

        GatewayServiceOperationException exception = assertThrows(
                GatewayServiceOperationException.class,
                () -> adapter.publish(service)
        );

        assertTrue(exception.getMessage().contains("plugin unavailable"));
        assertFalse(requests.stream().anyMatch(request ->
                "POST".equals(request.method()) && request.path().endsWith("/routes")
        ));
    }

    @Test
    void removesOnlyOwnedAccessPluginsWhenPublishingPublicService() {
        GatewayServiceSpec service = service();
        AtomicInteger routeGets = new AtomicInteger();
        handler = exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("GET".equals(method) && path.startsWith("/services/datascalpel-service-")) {
                respond(exchange, 200, kongService("kong-service-1", service));
            } else if ("PATCH".equals(method) && path.startsWith("/services/")) {
                respond(exchange, 200, kongService("kong-service-1", service));
            } else if ("GET".equals(method) && path.endsWith("/plugins")) {
                if (exchange.getRequestURI().getRawQuery().contains("key-auth")) {
                    respond(exchange, 200, "{\"data\":[" + keyAuthPlugin(service) + "]}");
                } else {
                    respond(exchange, 200, "{\"data\":[" + aclPlugin(service) + "]}");
                }
            } else if ("GET".equals(method) && path.startsWith("/routes/")) {
                if (routeGets.getAndIncrement() == 0) {
                    respond(exchange, 200, kongRoute("old-route", "kong-service-1", service));
                } else {
                    respond(exchange, 404, "");
                }
            } else if ("DELETE".equals(method)) {
                respond(exchange, 204, "");
            } else {
                respond(exchange, 201, kongRoute("public-route", "kong-service-1", service));
            }
        };

        GatewayServiceResult result = adapter.publish(service);

        assertEquals("public-route", result.externalRouteId());
        assertTrue(requests.stream().anyMatch(request ->
                "DELETE".equals(request.method()) && "/routes/old-route".equals(request.path())
        ));
        assertTrue(requests.stream().anyMatch(request ->
                "DELETE".equals(request.method()) && "/plugins/key-auth-plugin".equals(request.path())
        ));
        assertTrue(requests.stream().anyMatch(request ->
                "DELETE".equals(request.method()) && "/plugins/acl-plugin".equals(request.path())
        ));
    }

    @Test
    void inspectsServiceRouteAndPluginsReadOnlyAndReportsMissingRemoteObjects() {
        GatewayServiceSpec service = service();
        handler = exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/services/") && !path.endsWith("/plugins")) {
                respond(exchange, 200, kongService("kong-service-1", service));
            } else if (path.startsWith("/routes/")) {
                respond(exchange, 200, kongRoute("kong-route-1", "kong-service-1", service));
            } else {
                respond(exchange, 200, "{\"data\":[]}");
            }
        };
        GatewayInspectionResult synchronizedResult = adapter.inspect(new GatewayServiceInspectionSpec(
                service,
                new GatewayServiceReference(
                        service.id(), service.code(), "kong-service-1", "kong-route-1"
                ),
                true
        ));
        assertEquals(GatewayReconciliationStatus.IN_SYNC, synchronizedResult.status());
        assertTrue(requests.stream().allMatch(request -> "GET".equals(request.method())));

        requests.clear();
        handler = exchange -> respond(exchange, 404, "");
        GatewayInspectionResult missing = adapter.inspect(new GatewayServiceInspectionSpec(
                service,
                new GatewayServiceReference(service.id(), service.code(), "missing", "missing"),
                true
        ));
        assertEquals(GatewayReconciliationReason.REMOTE_MISSING, missing.reason());
        assertTrue(requests.stream().allMatch(request -> "GET".equals(request.method())));
    }

    @Test
    void reportsUpstreamComponentMismatchFromKongServiceResponse() {
        GatewayServiceSpec service = service();
        handler = exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/services/") && !path.endsWith("/plugins")) {
                respond(exchange, 200, kongServiceWithHost("kong-service-1", service, "other-engine.test"));
            } else if (path.startsWith("/routes/")) {
                respond(exchange, 200, kongRoute("kong-route-1", "kong-service-1", service));
            } else {
                respond(exchange, 200, "{\"data\":[]}");
            }
        };

        GatewayInspectionResult result = adapter.inspect(new GatewayServiceInspectionSpec(
                service,
                new GatewayServiceReference(
                        service.id(), service.code(), "kong-service-1", "kong-route-1"
                ),
                true
        ));

        assertEquals(GatewayReconciliationStatus.DRIFTED, result.status());
        assertEquals(GatewayReconciliationReason.CONFIG_MISMATCH, result.reason());
        assertTrue(requests.stream().allMatch(request -> "GET".equals(request.method())));
    }

    private int indexOfBody(String fragment) {
        for (int index = 0; index < requests.size(); index++) {
            if (requests.get(index).body().contains(fragment)) return index;
        }
        return -1;
    }

    private static GatewayServiceSpec service() {
        return new GatewayServiceSpec(
                UUID.fromString("6de591ba-cae3-45b5-bf76-cb254841cf9a"),
                "orders",
                "订单服务",
                3,
                "/open-api/v1/orders",
                "http://engine.test:8081/",
                "/open-api/v1/orders", DataServiceAccessMode.PUBLIC
        );
    }

    private static GatewayServiceSpec protectedService() {
        GatewayServiceSpec service = service();
        return new GatewayServiceSpec(
                service.id(),
                service.code(),
                service.name(),
                service.revision(),
                service.gatewayRoutePath(),
                service.upstreamUrl(),
                service.upstreamPath(),
                DataServiceAccessMode.SUBSCRIPTION_REQUIRED
        );
    }

    private static String keyAuthPlugin(GatewayServiceSpec service) {
        return """
                {
                  "id":"key-auth-plugin",
                  "name":"key-auth",
                  "service":{"id":"kong-service-1"},
                  "config":{
                    "key_names":["X-API-Key"],
                    "hide_credentials":true,
                    "key_in_header":true,
                    "key_in_query":false,
                    "key_in_body":false,
                    "run_on_preflight":true
                  },
                  "tags":["datascalpel","datascalpel-service-access","datascalpel-data-service-%s"]
                }
                """.formatted(service.id());
    }

    private static String aclPlugin(GatewayServiceSpec service) {
        return """
                {
                  "id":"acl-plugin",
                  "name":"acl",
                  "service":{"id":"kong-service-1"},
                  "config":{
                    "allow":["%s"],
                    "deny":[],
                    "hide_groups_header":true,
                    "always_use_authenticated_groups":false
                  },
                  "tags":["datascalpel","datascalpel-service-access","datascalpel-data-service-%s"]
                }
                """.formatted(KongGatewayManagedNames.serviceAclGroup(service.id()), service.id());
    }

    private static String kongService(String externalId, GatewayServiceSpec service, String... extraTags) {
        URI upstream = URI.create(service.upstreamUrl().replaceFirst("/+$", ""));
        return kongService(
                externalId,
                service,
                upstream.getScheme(),
                upstream.getHost(),
                upstream.getPort(),
                upstream.getRawPath(),
                extraTags
        );
    }

    private static String kongServiceWithHost(
            String externalId,
            GatewayServiceSpec service,
            String host
    ) {
        URI upstream = URI.create(service.upstreamUrl().replaceFirst("/+$", ""));
        return kongService(
                externalId,
                service,
                upstream.getScheme(),
                host,
                upstream.getPort(),
                upstream.getRawPath()
        );
    }

    private static String kongService(
            String externalId,
            GatewayServiceSpec service,
            String protocol,
            String host,
            int configuredPort,
            String path,
            String... extraTags
    ) {
        List<String> tags = new ArrayList<>(List.of(
                "datascalpel",
                "datascalpel-service",
                "datascalpel-data-service-" + service.id()
        ));
        tags.addAll(List.of(extraTags));
        int port = configuredPort >= 0
                ? configuredPort
                : "https".equalsIgnoreCase(protocol) ? 443 : 80;
        String pathJson = path == null || path.isBlank() ? "null" : "\"" + path + "\"";
        return """
                {
                  "id":"%s",
                  "name":"datascalpel-service-%s",
                  "protocol":"%s",
                  "host":"%s",
                  "port":%d,
                  "path":%s,
                  "tags":%s
                }
                """.formatted(
                externalId,
                service.id(),
                protocol,
                host,
                port,
                pathJson,
                jsonTags(tags)
        );
    }

    private static String kongRoute(
            String externalId,
            String serviceId,
            GatewayServiceSpec service,
            String... extraTags
    ) {
        List<String> tags = new ArrayList<>(List.of(
                "datascalpel",
                "datascalpel-service",
                "datascalpel-data-service-" + service.id()
        ));
        tags.addAll(List.of(extraTags));
        return """
                {
                  "id":"%s",
                  "name":"datascalpel-route-%s",
                  "paths":["%s"],
                  "methods":["POST"],
                  "protocols":["http","https"],
                  "strip_path":false,
                  "preserve_host":false,
                  "service":{"id":"%s"},
                  "tags":%s
                }
                """.formatted(
                externalId,
                service.id(),
                service.gatewayRoutePath(),
                serviceId,
                jsonTags(tags)
        );
    }

    private static String jsonTags(List<String> tags) {
        return "[" + tags.stream().map(tag -> "\"" + tag + "\"").collect(java.util.stream.Collectors.joining(",")) + "]";
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

    private record RecordedRequest(String method, String path, String query, String authorization, String body) {
    }
}
