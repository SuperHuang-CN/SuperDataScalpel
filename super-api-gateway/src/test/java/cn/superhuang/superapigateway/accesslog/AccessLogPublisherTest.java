package cn.superhuang.superapigateway.accesslog;

import cn.superhuang.superapigateway.configuration.SuperApiGatewayProperties;
import cn.superhuang.superapigateway.runtime.GatewayTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccessLogPublisherTest {
    @Test void synchronousFailureRetriesSameEventWithoutBlockingCaller() throws Exception {
        var registry = new SimpleMeterRegistry();
        @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("metadata cold"))
                .thenReturn(CompletableFuture.completedFuture(null));
        var publisher = new AccessLogPublisher(new SuperApiGatewayProperties(null, null, null, null, null),
                kafka, new ObjectMapper(), registry, new GatewayTelemetry(registry));
        publisher.start();
        try {
            publisher.publish(event());
            verify(kafka, timeout(3000).times(2)).send(eq("datascalpel.gateway.access.v1"), eq("event-1"), anyString());
            for (int i = 0; i < 100 && publisher.deliveryStatus().delivered() == 0; i++) Thread.sleep(10);
            assertThat(publisher.deliveryStatus().delivered()).isEqualTo(1);
            assertThat(publisher.deliveryStatus().failures()).isEqualTo(1);
            assertThat(publisher.deliveryStatus().dropped()).isZero();
            var json = ArgumentCaptor.forClass(String.class);
            verify(kafka, times(2)).send(anyString(), anyString(), json.capture());
            assertThat(json.getAllValues().getFirst()).isEqualTo(json.getAllValues().getLast());
            assertThat(json.getValue()).contains("\"schema_version\":\"1.0\"", "\"external_id\":\"local-id\"");
        } finally { publisher.stop(); }
    }

    @Test void persistentAsyncFailureIsBoundedAndCounted() throws Exception {
        var registry = new SimpleMeterRegistry();
        @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString())).thenAnswer(call -> CompletableFuture.failedFuture(new IllegalStateException()));
        var publisher = new AccessLogPublisher(new SuperApiGatewayProperties(null, null, null, null, null),
                kafka, new ObjectMapper(), registry, new GatewayTelemetry(registry));
        publisher.start();
        try {
            publisher.publish(event());
            for (int i = 0; i < 300 && publisher.deliveryStatus().dropped() == 0; i++) Thread.sleep(10);
            assertThat(publisher.deliveryStatus().dropped()).isEqualTo(1);
            assertThat(publisher.deliveryStatus().failures()).isEqualTo(3);
            verify(kafka, times(3)).send(anyString(), anyString(), anyString());
        } finally { publisher.stop(); }
    }

    private GatewayAccessLog event() {
        return new GatewayAccessLog("1.0", "gateway.access", "event-1", Instant.now(), "DATASCALPEL",
                System.currentTimeMillis(), "test-node", new GatewayAccessLog.Reference("id", "service", "local-id"),
                null, null, new GatewayAccessLog.Request("request-1", "GET", "/test", null),
                new GatewayAccessLog.Response(200, null), new GatewayAccessLog.Latencies(1, 1L, null, null), null, null);
    }
}
