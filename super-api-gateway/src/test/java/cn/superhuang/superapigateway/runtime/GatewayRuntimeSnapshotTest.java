package cn.superhuang.superapigateway.runtime;

import cn.superhuang.superapigateway.controlplane.domain.AccessMode;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRuntimeSnapshotTest {

    private final PathPatternParser parser = new PathPatternParser();

    @Test
    void matchesByMethodFirstSegmentAndSpecificity() {
        var general = route("general", "/api/**", 0);
        var specific = route("specific", "/api/orders/{id}", 0);
        var snapshot = new GatewayRuntimeSnapshot(
                7,
                Map.of(
                        new GatewayRuntimeSnapshot.RouteIndexKey("GET", "api"),
                        List.of(general, specific)
                ),
                Map.of(),
                Set.of()
        );

        var result = snapshot.match(HttpMethod.GET, PathContainer.parsePath("/api/orders/42"));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().routeCode()).isEqualTo("specific");
        assertThat(snapshot.match(HttpMethod.POST, PathContainer.parsePath("/api/orders/42")))
                .isEmpty();
    }

    @Test
    void resolvesApiKeyAndSubscriptionFromImmutableSnapshot() {
        UUID consumerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        var consumer = new GatewayRuntimeSnapshot.RuntimeConsumer(consumerId, "client-a", true);
        var snapshot = new GatewayRuntimeSnapshot(
                1,
                Map.of(),
                Map.of("hash", consumer),
                Set.of(new GatewayRuntimeSnapshot.SubscriptionKey(consumerId, serviceId))
        );

        assertThat(snapshot.consumer("hash")).isEqualTo(consumer);
        assertThat(snapshot.subscribed(consumerId, serviceId)).isTrue();
        assertThat(snapshot.subscribed(consumerId, UUID.randomUUID())).isFalse();
    }

    private GatewayRuntimeSnapshot.RuntimeRoute route(String code, String path, int order) {
        UUID routeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Route gatewayRoute = Route.async()
                .id(code)
                .uri(URI.create("http://localhost:9999"))
                .asyncPredicate(exchange -> Mono.just(true))
                .build();
        return new GatewayRuntimeSnapshot.RuntimeRoute(
                routeId,
                code,
                serviceId,
                "service",
                AccessMode.PUBLIC,
                path,
                parser.parse(path),
                order,
                0,
                gatewayRoute
        );
    }
}
