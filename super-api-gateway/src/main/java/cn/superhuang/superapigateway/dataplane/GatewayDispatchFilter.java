package cn.superhuang.superapigateway.dataplane;

import cn.superhuang.superapigateway.accesslog.AccessLogPublisher;
import cn.superhuang.superapigateway.accesslog.GatewayAccessLog;
import cn.superhuang.superapigateway.controlplane.domain.AccessMode;
import cn.superhuang.superapigateway.controlplane.service.ApiKeySecretService;
import cn.superhuang.superapigateway.controlplane.web.ProblemResponseWriter;
import cn.superhuang.superapigateway.runtime.GatewayRuntimeCoordinator;
import cn.superhuang.superapigateway.runtime.GatewayRuntimeHolder;
import cn.superhuang.superapigateway.runtime.GatewayRuntimeSnapshot;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class GatewayDispatchFilter implements GlobalFilter, Ordered {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String CONSUMER_ID_HEADER = "X-Super-Gateway-Consumer-Id";
    public static final String CONSUMER_CODE_HEADER = "X-Super-Gateway-Consumer-Code";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String PROXY_REQUEST_ATTRIBUTE =
            GatewayDispatchFilter.class.getName() + ".proxyRequest";

    private final GatewayRuntimeHolder runtime;
    private final ApiKeySecretService secrets;
    private final ProblemResponseWriter problems;
    private final AccessLogPublisher accessLogs;
    private final GatewayRuntimeCoordinator coordinator;

    public GatewayDispatchFilter(
            GatewayRuntimeHolder runtime,
            ApiKeySecretService secrets,
            ProblemResponseWriter problems,
            AccessLogPublisher accessLogs,
            GatewayRuntimeCoordinator coordinator
    ) {
        this.runtime = runtime;
        this.secrets = secrets;
        this.problems = problems;
        this.accessLogs = accessLogs;
        this.coordinator = coordinator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startedNanos = System.nanoTime();
        String requestId = requestId(exchange);
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);

        var method = exchange.getRequest().getMethod();
        GatewayRuntimeSnapshot snapshot = runtime.snapshot();
        var matched = snapshot.match(
                method,
                PathContainer.parsePath(exchange.getRequest().getPath().value())
        ).orElse(null);
        if (matched == null) {
            registerAccessLog(
                    exchange,
                    startedNanos,
                    requestId,
                    method.name(),
                    null,
                    null
            );
            return problems.write(
                    exchange,
                    HttpStatus.NOT_FOUND,
                    "GATEWAY_ROUTE_NOT_FOUND",
                    "Route not found",
                    "No enabled gateway route matches this request"
            );
        }

        String plaintextKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        GatewayRuntimeSnapshot.RuntimeConsumer consumer = plaintextKey == null
                ? null : snapshot.consumer(secrets.sha256(plaintextKey));

        if (matched.accessMode() == AccessMode.SUBSCRIPTION_REQUIRED && consumer == null) {
            registerAccessLog(
                    exchange,
                    startedNanos,
                    requestId,
                    method.name(),
                    matched,
                    null
            );
            exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "ApiKey");
            return problems.write(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "GATEWAY_API_KEY_INVALID",
                    "API key required",
                    "A valid X-API-Key is required"
            );
        }
        if (matched.accessMode() == AccessMode.SUBSCRIPTION_REQUIRED
                && consumer != null
                && !consumer.enabled()) {
            registerAccessLog(
                    exchange,
                    startedNanos,
                    requestId,
                    method.name(),
                    matched,
                    consumer
            );
            return problems.write(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    "GATEWAY_CONSUMER_DISABLED",
                    "Consumer disabled",
                    "The Consumer associated with this API key is disabled"
            );
        }
        if (matched.accessMode() == AccessMode.SUBSCRIPTION_REQUIRED
                && !snapshot.subscribed(consumer.id(), matched.serviceId())) {
            registerAccessLog(
                    exchange,
                    startedNanos,
                    requestId,
                    method.name(),
                    matched,
                    consumer
            );
            return problems.write(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    "GATEWAY_SUBSCRIPTION_REQUIRED",
                    "Subscription required",
                    "The Consumer is not subscribed to this service"
            );
        }

        String rewrittenPath = stripPrefix(
                exchange.getRequest().getPath().value(),
                matched.stripPrefixSegments()
        );
        var consumerIdentity = consumer != null && consumer.enabled() ? consumer : null;
        registerAccessLog(
                exchange,
                startedNanos,
                requestId,
                method.name(),
                matched,
                consumerIdentity
        );
        exchange.getAttributes().put(PROXY_REQUEST_ATTRIBUTE, Boolean.TRUE);
        ServerWebExchange routed = exchange.mutate()
                .request(builder -> builder
                        .path(rewrittenPath)
                        .headers(headers -> {
                            headers.remove(API_KEY_HEADER);
                            headers.remove(CONSUMER_ID_HEADER);
                            headers.remove(CONSUMER_CODE_HEADER);
                            if (consumerIdentity != null) {
                                headers.set(CONSUMER_ID_HEADER, consumerIdentity.id().toString());
                                headers.set(CONSUMER_CODE_HEADER, consumerIdentity.code());
                            }
                            headers.set(REQUEST_ID_HEADER, requestId);
                        }))
                .build();
        routed.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, matched.gatewayRoute());

        return chain.filter(routed);
    }

    private void registerAccessLog(
            ServerWebExchange exchange,
            long startedNanos,
            String requestId,
            String method,
            GatewayRuntimeSnapshot.RuntimeRoute matched,
            GatewayRuntimeSnapshot.RuntimeConsumer consumer
    ) {
        AtomicBoolean published = new AtomicBoolean();
        Runnable publish = () -> {
            if (!published.compareAndSet(false, true)) return;
            int status = exchange.getResponse().getStatusCode() == null
                    ? 500 : exchange.getResponse().getStatusCode().value();
            accessLogs.publish(new GatewayAccessLog(
                    1,
                    Instant.now(),
                    requestId,
                    coordinator.instanceId(),
                    matched == null ? null : matched.serviceId().toString(),
                    matched == null ? null : matched.serviceCode(),
                    matched == null ? null : matched.routeId().toString(),
                    matched == null ? null : matched.routeCode(),
                    consumer == null ? null : consumer.id().toString(),
                    consumer == null ? null : consumer.code(),
                    method,
                    matched == null ? null : matched.pathTemplate(),
                    status,
                    (System.nanoTime() - startedNanos) / 1_000_000
            ));
        };
        exchange.getResponse().beforeCommit(() -> {
            publish.run();
            return Mono.empty();
        });
    }

    @Override
    public int getOrder() {
        return -1000;
    }

    private static String requestId(ServerWebExchange exchange) {
        String existing = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (existing != null && existing.matches("[A-Za-z0-9._-]{8,128}")) return existing;
        return UUID.randomUUID().toString();
    }

    private static String stripPrefix(String path, int segments) {
        if (segments <= 0) return path;
        int index = 0;
        for (int removed = 0; removed < segments; removed++) {
            index = path.indexOf('/', index + 1);
            if (index < 0) return "/";
        }
        return path.substring(index).isEmpty() ? "/" : path.substring(index);
    }
}
