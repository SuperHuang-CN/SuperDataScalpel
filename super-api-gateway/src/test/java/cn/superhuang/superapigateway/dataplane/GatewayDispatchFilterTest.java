package cn.superhuang.superapigateway.dataplane;

import cn.superhuang.superapigateway.accesslog.AccessLogPublisher;
import cn.superhuang.superapigateway.accesslog.GatewayAccessLog;
import cn.superhuang.superapigateway.controlplane.domain.AccessMode;
import cn.superhuang.superapigateway.controlplane.service.ApiKeySecretService;
import cn.superhuang.superapigateway.controlplane.web.ProblemResponseWriter;
import cn.superhuang.superapigateway.runtime.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GatewayDispatchFilterTest {
    private final GatewayRuntimeHolder holder = new GatewayRuntimeHolder();
    private final AccessLogPublisher logs = mock(AccessLogPublisher.class);
    private final ProblemResponseWriter problems = new ProblemResponseWriter(new ObjectMapper());
    private final GatewayDispatchFilter filter = new GatewayDispatchFilter(holder, new ApiKeySecretService(), problems,
            logs, mock(GatewayRuntimeCoordinator.class), new TrafficGuard());

    private void route() {
        var route = new GatewayRuntimeSnapshot.RuntimeRoute(UUID.randomUUID(), "test", UUID.randomUUID(), "test",
                AccessMode.PUBLIC, "/test/{id}", new PathPatternParser().parse("/test/{id}"), 0, 0, null,
                Route.async().id("test").uri(URI.create("http://localhost:1")).asyncPredicate(e -> Mono.just(true)).build());
        holder.install(new GatewayRuntimeSnapshot(1, Map.of(new GatewayRuntimeSnapshot.RouteIndexKey("GET", "test"), List.of(route)), Map.of(), Set.of()));
    }

    @Test void missingRouteIsLoggedOnceWithoutRawPathOrQuery() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/secret-value?q=secret"));
        filter.filter(exchange, e -> Mono.error(new AssertionError())).block();
        var event = captured();
        assertThat(event.response().status()).isEqualTo(404);
        assertThat(event.request().path()).isEqualTo("/_unmatched");
    }

    @Test void identityHeadersAreStrippedAndCallerRequestIdCannotCollide() {
        route();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test/sensitive?secret=value")
                .header("X-API-Key", "key").header("X-Super-Gateway-Admin-Token", "secret")
                .header("X-Super-Gateway-Consumer-Id", "spoof").header("X-Request-ID", "spoof"));
        filter.filter(exchange, e -> {
            assertThat(e.getRequest().getHeaders().getFirst("X-API-Key")).isNull();
            assertThat(e.getRequest().getHeaders().getFirst("X-Super-Gateway-Admin-Token")).isNull();
            assertThat(e.getRequest().getHeaders().getFirst("X-Super-Gateway-Consumer-Id")).isNull();
            return Mono.empty();
        }).block();
        var event = captured();
        assertThat(event.request().path()).isEqualTo("/test/{id}");
        assertThat(event.request().id()).isNotEqualTo("spoof");
    }

    @Test void cancellationIsNotRecordedAsSuccess() {
        route();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test/1"));
        StepVerifier.create(filter.filter(exchange, e -> Mono.never())).thenCancel().verify();
        assertThat(captured().response().status()).isEqualTo(499);
    }

    @Test void proxyFailureIsRecordedAfterProblemRendering() {
        route();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test/1"));
        filter.filter(exchange, e -> Mono.error(new java.net.ConnectException("private backend")))
                .onErrorResume(e -> new GatewayProxyErrorWebExceptionHandler(problems).handle(exchange, e)).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(captured().response().status()).isEqualTo(502);
        assertThat(exchange.getResponse().getBodyAsString().block()).doesNotContain("private backend");
    }

    @Test void committedStreamFailureRecordsFailureInsteadOfHttpHeadersSuccess() {
        route();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test/1"));
        var upstream = mock(reactor.netty.http.client.HttpClientResponse.class);
        when(upstream.status()).thenReturn(io.netty.handler.codec.http.HttpResponseStatus.OK);
        exchange.getAttributes().put(org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR, upstream);
        StepVerifier.create(filter.filter(exchange, e -> e.getResponse().setComplete()
                .then(Mono.error(new IllegalStateException("stream terminated")))))
                .expectError(IllegalStateException.class).verify();
        var event = captured();
        assertThat(event.response().status()).isEqualTo(502);
        assertThat(event.upstreamStatus()).isEqualTo("200");
    }

    private GatewayAccessLog captured() {
        var captor = ArgumentCaptor.forClass(GatewayAccessLog.class);
        verify(logs, times(1)).publish(captor.capture());
        return captor.getValue();
    }
}
