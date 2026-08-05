package cn.superhuang.superapigateway.runtime;

import cn.superhuang.superapigateway.controlplane.domain.ApiKeyStatus;
import cn.superhuang.superapigateway.controlplane.domain.GatewayConsumerEntity;
import cn.superhuang.superapigateway.controlplane.domain.GatewayHttpMethod;
import cn.superhuang.superapigateway.controlplane.domain.GatewayServiceEntity;
import cn.superhuang.superapigateway.controlplane.domain.SubscriptionStatus;
import cn.superhuang.superapigateway.controlplane.repository.GatewayApiKeyRepository;
import cn.superhuang.superapigateway.controlplane.repository.GatewayConsumerRepository;
import cn.superhuang.superapigateway.controlplane.repository.GatewayRouteRepository;
import cn.superhuang.superapigateway.controlplane.repository.GatewayServiceRepository;
import cn.superhuang.superapigateway.controlplane.repository.GatewaySubscriptionRepository;
import cn.superhuang.superapigateway.controlplane.service.ConfigurationRevisionService;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.RouteMetadataUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.pattern.PathPatternParser;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

@Service
public class GatewayRuntimeSnapshotLoader {

    private final GatewayServiceRepository services;
    private final GatewayRouteRepository routes;
    private final GatewayConsumerRepository consumers;
    private final GatewayApiKeyRepository apiKeys;
    private final GatewaySubscriptionRepository subscriptions;
    private final ConfigurationRevisionService revisions;

    public GatewayRuntimeSnapshotLoader(
            GatewayServiceRepository services,
            GatewayRouteRepository routes,
            GatewayConsumerRepository consumers,
            GatewayApiKeyRepository apiKeys,
            GatewaySubscriptionRepository subscriptions,
            ConfigurationRevisionService revisions
    ) {
        this.services = services;
        this.routes = routes;
        this.consumers = consumers;
        this.apiKeys = apiKeys;
        this.subscriptions = subscriptions;
        this.revisions = revisions;
    }

    @Transactional(readOnly = true)
    public GatewayRuntimeSnapshot load() {
        long revision = revisions.currentRevision();
        Map<UUID, GatewayServiceEntity> enabledServices = new HashMap<>();
        services.findAll().stream().filter(GatewayServiceEntity::isEnabled)
                .forEach(service -> enabledServices.put(service.getId(), service));

        PathPatternParser parser = new PathPatternParser();
        Map<GatewayRuntimeSnapshot.RouteIndexKey,
                java.util.List<GatewayRuntimeSnapshot.RuntimeRoute>> routeIndex = new HashMap<>();

        routes.findAllByEnabledTrue().forEach(route -> {
            GatewayServiceEntity service = enabledServices.get(route.getServiceId());
            if (service == null) return;
            var pathPattern = parser.parse(route.getPathPattern());
            Route gatewayRoute = Route.async()
                    .id("sag-" + route.getId())
                    .order(route.getOrder())
                    .uri(URI.create(service.getUpstreamUri()))
                    .metadata(Map.of(
                            RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, service.getConnectTimeoutMs(),
                            RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR,
                            (long) service.getResponseTimeoutMs()
                    ))
                    .asyncPredicate(exchange -> reactor.core.publisher.Mono.just(true))
                    .build();
            var runtimeRoute = new GatewayRuntimeSnapshot.RuntimeRoute(
                    route.getId(),
                    route.getCode(),
                    service.getId(),
                    service.getCode(),
                    service.getAccessMode(),
                    route.getPathPattern(),
                    pathPattern,
                    route.getOrder(),
                    route.getStripPrefixSegments(),
                    gatewayRoute
            );
            String firstSegment = GatewayRuntimeSnapshot.firstSegment(route.getPathPattern());
            for (GatewayHttpMethod method : route.getMethods()) {
                var key = new GatewayRuntimeSnapshot.RouteIndexKey(method.name(), firstSegment);
                routeIndex.computeIfAbsent(key, ignored -> new ArrayList<>()).add(runtimeRoute);
            }
        });

        Map<UUID, GatewayConsumerEntity> allConsumers = new HashMap<>();
        consumers.findAll().forEach(consumer -> allConsumers.put(consumer.getId(), consumer));
        Map<UUID, GatewayConsumerEntity> enabledConsumers = new HashMap<>();
        allConsumers.values().stream().filter(GatewayConsumerEntity::isEnabled)
                .forEach(consumer -> enabledConsumers.put(consumer.getId(), consumer));

        Map<String, GatewayRuntimeSnapshot.RuntimeConsumer> keyIndex = new HashMap<>();
        apiKeys.findAllByStatus(ApiKeyStatus.ACTIVE).forEach(key -> {
            GatewayConsumerEntity consumer = allConsumers.get(key.getConsumerId());
            if (consumer != null) {
                keyIndex.put(
                        key.getSecretHash(),
                        new GatewayRuntimeSnapshot.RuntimeConsumer(
                                consumer.getId(),
                                consumer.getCode(),
                                consumer.isEnabled()
                        )
                );
            }
        });

        var granted = new HashSet<GatewayRuntimeSnapshot.SubscriptionKey>();
        subscriptions.findAllByStatus(SubscriptionStatus.ACTIVE).forEach(subscription -> {
            if (enabledConsumers.containsKey(subscription.getConsumerId())
                    && enabledServices.containsKey(subscription.getServiceId())) {
                granted.add(new GatewayRuntimeSnapshot.SubscriptionKey(
                        subscription.getConsumerId(),
                        subscription.getServiceId()
                ));
            }
        });

        return new GatewayRuntimeSnapshot(revision, routeIndex, keyIndex, granted);
    }
}
