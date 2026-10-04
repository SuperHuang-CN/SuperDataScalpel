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
    private static final String ACCESS_LOG_ATTRIBUTE = GatewayDispatchFilter.class.getName() + ".accessLog";
    private static final String PROXY_START_ATTRIBUTE = GatewayDispatchFilter.class.getName() + ".proxyStart";
    private static final String TERMINAL_STATUS_ATTRIBUTE = GatewayDispatchFilter.class.getName() + ".terminalStatus";

    private final GatewayRuntimeHolder runtime;
    private final ApiKeySecretService secrets;
    private final ProblemResponseWriter problems;
    private final AccessLogPublisher accessLogs;
    private final GatewayRuntimeCoordinator coordinator;
    private final TrafficGuard trafficGuard;

    public GatewayDispatchFilter(
            GatewayRuntimeHolder runtime,
            ApiKeySecretService secrets,
            ProblemResponseWriter problems,
            AccessLogPublisher accessLogs,
            GatewayRuntimeCoordinator coordinator,
            TrafficGuard trafficGuard
    ) {
        this.runtime = runtime;
        this.secrets = secrets;
        this.problems = problems;
        this.accessLogs = accessLogs;
        this.coordinator = coordinator;
        this.trafficGuard = trafficGuard;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return dispatch(exchange, chain).doOnSuccess(ignored -> publishAccessLog(exchange))
                .doOnError(failure -> {
                    // A truncated stream must not be recorded as a successful 200 response.
                    if (exchange.getResponse().isCommitted()) {
                        exchange.getAttributes().put(TERMINAL_STATUS_ATTRIBUTE, 502);
                        publishAccessLog(exchange);
                    }
                })
                .doFinally(signal -> {
                    if (signal == reactor.core.publisher.SignalType.CANCEL) {
                        exchange.getAttributes().put(TERMINAL_STATUS_ATTRIBUTE, 499);
                        publishAccessLog(exchange);
                    }
                });
    }

    private Mono<Void> dispatch(ServerWebExchange exchange, GatewayFilterChain chain) {
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

        String rewrittenPath = matched.upstreamPath() != null
                ? matched.upstreamPath()
                : stripPrefix(exchange.getRequest().getPath().value(), matched.stripPrefixSegments());
        var consumerIdentity = consumer != null && consumer.enabled() ? consumer : null;
        registerAccessLog(
                exchange,
                startedNanos,
                requestId,
                method.name(),
                matched,
                consumerIdentity
        );
        var policy = matched.trafficPolicy();
        var remote = exchange.getRequest().getRemoteAddress();
        if (!policy.permits(remote == null ? null : remote.getAddress())) {
            return problems.write(exchange, HttpStatus.FORBIDDEN, "GATEWAY_IP_DENIED", "IP denied",
                    "The TCP client address is not permitted by this service policy");
        }
        long maxBytes = policy.settings().maxRequestBytes();
        if (maxBytes > 0 && exchange.getRequest().getHeaders().getContentLength() > maxBytes) {
            return problems.write(exchange, HttpStatus.PAYLOAD_TOO_LARGE, "GATEWAY_REQUEST_TOO_LARGE",
                    "Request too large", "The request exceeds the configured size limit");
        }
        int subscriptionRate = consumerIdentity == null ? 0 : snapshot.subscriptionRate(consumerIdentity.id(), matched.serviceId());
        int consumerRate = policy.settings().consumerRequestsPerSecond();
        if (subscriptionRate > 0) consumerRate = consumerRate == 0 ? subscriptionRate : Math.min(consumerRate, subscriptionRate);
        var lease = trafficGuard.acquire(matched.serviceId(), consumerIdentity == null ? null : consumerIdentity.id(),
                policy.settings().requestsPerSecond(), consumerRate,
                policy.settings().maxConcurrentRequests());
        if (lease == null) {
            exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, "1");
            return problems.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "GATEWAY_CAPACITY_EXCEEDED",
                    "Gateway capacity exceeded", "The per-node service or consumer rate/concurrency limit was reached");
        }
        exchange.getAttributes().put(PROXY_REQUEST_ATTRIBUTE, Boolean.TRUE);
        exchange.getAttributes().put(PROXY_START_ATTRIBUTE, System.nanoTime());
        ServerWebExchange routed = exchange.mutate()
                .request(builder -> builder
                        .path(rewrittenPath)
                        .headers(headers -> {
                            headers.remove(API_KEY_HEADER);
                            headers.remove("X-Super-Gateway-Admin-Token");
                            headers.remove(CONSUMER_ID_HEADER);
                            headers.remove(CONSUMER_CODE_HEADER);
                            if (consumerIdentity != null) {
                                headers.set(CONSUMER_ID_HEADER, consumerIdentity.id().toString());
                                headers.set(CONSUMER_CODE_HEADER, consumerIdentity.code());
                            }
                            headers.set(REQUEST_ID_HEADER, requestId);
                        }))
                .build();
        if (maxBytes > 0) {
            var request = new org.springframework.http.server.reactive.ServerHttpRequestDecorator(routed.getRequest()) {
                @Override
                public reactor.core.publisher.Flux<org.springframework.core.io.buffer.DataBuffer> getBody() {
                    return reactor.core.publisher.Flux.defer(() -> {
                        var received = new java.util.concurrent.atomic.AtomicLong();
                        return super.getBody().handle((buffer, sink) -> {
                            if (received.addAndGet(buffer.readableByteCount()) > maxBytes) {
                                org.springframework.core.io.buffer.DataBufferUtils.release(buffer);
                                sink.error(new RequestSizeLimitException());
                            } else sink.next(buffer);
                        });
                    });
                }
            };
            routed = routed.mutate().request(request).build();
        }
        routed.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, matched.gatewayRoute());
        ServerWebExchange protectedExchange = routed;
        return Mono.defer(() -> chain.filter(protectedExchange)).doFinally(signal -> lease.close());
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
        long startedAtEpochMs = System.currentTimeMillis() - (System.nanoTime() - startedNanos) / 1_000_000;
        Runnable publish = () -> {
            if (!published.compareAndSet(false, true)) return;
            int status = exchange.getResponse().getStatusCode() == null
                    ? 200 : exchange.getResponse().getStatusCode().value();
            Integer terminalStatus = exchange.getAttribute(TERMINAL_STATUS_ATTRIBUTE);
            if (terminalStatus != null) status = terminalStatus;
            long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;
            Long proxyStart = exchange.getAttribute(PROXY_START_ATTRIBUTE);
            Object clientResponse = exchange.getAttribute(ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR);
            String upstreamStatus = clientResponse instanceof reactor.netty.http.client.HttpClientResponse response
                    ? Integer.toString(response.status().code()) : null;
            Long proxyMs = upstreamStatus == null || proxyStart == null ? null
                    : (System.nanoTime() - proxyStart) / 1_000_000;
            var remote = exchange.getRequest().getRemoteAddress();
            accessLogs.publish(new GatewayAccessLog(
                    "1.0",
                    "gateway.access",
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    "DATASCALPEL",
                    startedAtEpochMs,
                    coordinator.instanceId(),
                    matched == null ? new GatewayAccessLog.Reference("unmatched", "unmatched", null)
                            : new GatewayAccessLog.Reference(matched.serviceId().toString(),
                            matched.serviceCode(), matched.serviceExternalId()),
                    matched == null ? null : new GatewayAccessLog.Reference(matched.routeId().toString(),
                            matched.routeCode(), matched.routeExternalId()),
                    consumer == null ? null : new GatewayAccessLog.Consumer(consumer.id().toString(),
                            consumer.code(), consumer.externalId()),
                    new GatewayAccessLog.Request(requestId, method, matched == null ? "/_unmatched"
                            : matched.pathTemplate(), knownLength(exchange.getRequest().getHeaders().getContentLength())),
                    new GatewayAccessLog.Response(status, knownLength(exchange.getResponse().getHeaders().getContentLength())),
                    new GatewayAccessLog.Latencies(durationMs, proxyMs == null ? durationMs : Math.max(0, durationMs - proxyMs),
                            proxyMs, null),
                    remote == null || remote.getAddress() == null ? null : remote.getAddress().getHostAddress(),
                    upstreamStatus
            ));
        };
        exchange.getAttributes().put(ACCESS_LOG_ATTRIBUTE, publish);
        exchange.getResponse().beforeCommit(() -> {
            // Proxy failures are rendered by the outer exception handler after this filter unwinds.
            // Normal proxy responses are recorded only after the streamed body completes.
            if (exchange.getAttribute(ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR) == null) publish.run();
            return Mono.empty();
        });
    }

    private static Long knownLength(long length) { return length < 0 ? null : length; }

    public static final class RequestSizeLimitException extends RuntimeException {}

    static void publishAccessLog(ServerWebExchange exchange) {
        Runnable publish = exchange.getAttribute(ACCESS_LOG_ATTRIBUTE);
        if (publish != null) publish.run();
    }

    @Override
    public int getOrder() {
        return -1000;
    }

    private static String requestId(ServerWebExchange exchange) {
        // An untrusted caller must not collapse distinct calls through the ingestion deduplication key.
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
