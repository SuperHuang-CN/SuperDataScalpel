package cn.superhuang.superapigateway.runtime;

import cn.superhuang.superapigateway.controlplane.domain.AccessMode;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class GatewayRuntimeSnapshot {

    private static final Comparator<RuntimeRoute> ROUTE_ORDER =
            Comparator.comparingInt(RuntimeRoute::order)
                    .thenComparing(
                            RuntimeRoute::pathPattern,
                            PathPattern.SPECIFICITY_COMPARATOR
                    )
                    .thenComparing(RuntimeRoute::routeCode);

    private final long revision;
    private final Map<RouteIndexKey, List<RuntimeRoute>> routeIndex;
    private final Map<String, RuntimeConsumer> apiKeys;
    private final Set<SubscriptionKey> subscriptions;

    public GatewayRuntimeSnapshot(
            long revision,
            Map<RouteIndexKey, List<RuntimeRoute>> routeIndex,
            Map<String, RuntimeConsumer> apiKeys,
            Set<SubscriptionKey> subscriptions
    ) {
        this.revision = revision;
        this.routeIndex = routeIndex.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream().sorted(ROUTE_ORDER).toList()
                )
        );
        this.apiKeys = Map.copyOf(apiKeys);
        this.subscriptions = Set.copyOf(subscriptions);
    }

    public static GatewayRuntimeSnapshot empty() {
        return new GatewayRuntimeSnapshot(0, Map.of(), Map.of(), Set.of());
    }

    public Optional<RuntimeRoute> match(HttpMethod method, PathContainer path) {
        String firstSegment = firstSegment(path.value());
        if (firstSegment == null) return Optional.empty();
        List<RuntimeRoute> candidates = routeIndex.get(
                new RouteIndexKey(method.name(), firstSegment)
        );
        if (candidates == null) return Optional.empty();
        return candidates.stream().filter(route -> route.pathPattern().matches(path)).findFirst();
    }

    public RuntimeConsumer consumer(String hash) {
        return hash == null ? null : apiKeys.get(hash);
    }

    public boolean subscribed(UUID consumerId, UUID serviceId) {
        return subscriptions.contains(new SubscriptionKey(consumerId, serviceId));
    }

    public long revision() {
        return revision;
    }

    public static String firstSegment(String path) {
        if (path == null || path.length() < 2 || path.charAt(0) != '/') return null;
        int nextSlash = path.indexOf('/', 1);
        return nextSlash < 0 ? path.substring(1) : path.substring(1, nextSlash);
    }

    public record RouteIndexKey(String method, String firstSegment) {
    }

    public record RuntimeConsumer(UUID id, String code, boolean enabled) {
    }

    public record SubscriptionKey(UUID consumerId, UUID serviceId) {
    }

    public record RuntimeRoute(
            UUID routeId,
            String routeCode,
            UUID serviceId,
            String serviceCode,
            AccessMode accessMode,
            String pathTemplate,
            PathPattern pathPattern,
            int order,
            int stripPrefixSegments,
            Route gatewayRoute
    ) {
    }
}
