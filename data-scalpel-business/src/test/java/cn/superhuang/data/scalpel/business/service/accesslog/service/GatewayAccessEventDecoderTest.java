package cn.superhuang.data.scalpel.business.service.accesslog.service;

import cn.superhuang.data.scalpel.business.service.accesslog.domain.GatewayAccessIdentityResolutionStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class GatewayAccessEventDecoderTest {
    private final GatewayAccessEventDecoder decoder = new GatewayAccessEventDecoder(JsonMapper.builder().build());
    @Test void selfGatewayUsesExternalIdsNotHumanServiceNames() {
        UUID id = UUID.randomUUID(); UUID eventId = UUID.randomUUID();
        var record = decode(eventId, event(id, eventId, "DATASCALPEL", "human-readable-code", "route-code", "200"));
        assertThat(record.dataServiceId()).isEqualTo(id);
        assertThat(record.identityResolutionStatus()).isEqualTo(GatewayAccessIdentityResolutionStatus.ANONYMOUS);
        assertThat(record.gatewayError()).isFalse();
        assertThat(record.upstreamError()).isFalse();
        assertThat(record.requestPath()).doesNotContain("secret");
    }
    @Test void kongManagedNamesStayCompatible() {
        UUID id = UUID.randomUUID(); UUID eventId = UUID.randomUUID();
        var record = decode(eventId, event(id, eventId, "KONG", "datascalpel-service-" + id, "datascalpel-route-" + id, "503"));
        assertThat(record.dataServiceId()).isEqualTo(id);
        assertThat(record.upstreamError()).isTrue();
    }
    @Test void mismatchedKafkaKeyIsRejected() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> decode(UUID.randomUUID(), event(id, UUID.randomUUID(), "DATASCALPEL", "service", "route", "200")))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void truncatedSuccessfulUpstreamIsGatewayError() {
        UUID eventId = UUID.randomUUID();
        var payload = event(UUID.randomUUID(), eventId, "DATASCALPEL", "service", "route", "200")
                .replace("\"status\":200", "\"status\":502");
        var record = decode(eventId, payload);
        assertThat(record.gatewayError()).isTrue();
        assertThat(record.upstreamError()).isFalse();
    }
    @Test void sizeRejectionIsAttributedToGateway() {
        UUID eventId = UUID.randomUUID();
        var payload = event(UUID.randomUUID(), eventId, "DATASCALPEL", "service", "route", "413")
                .replace("\"upstream_status\":\"413\"", "\"upstream_status\":null");
        assertThat(decode(eventId, payload).gatewayRejected()).isTrue();
    }
    private GatewayAccessRecord decode(UUID key, String value) {
        return decoder.decode(new ConsumerRecord<>("test", 0, 1, key.toString(), value), Instant.now(), Duration.ofDays(7));
    }
    private String event(UUID id, UUID eventId, String provider, String service, String route, String upstream) {
        return """
                {"schema_version":"1.0","event_type":"gateway.access","event_id":"%s",
                 "gateway_provider":"%s","started_at_epoch_ms":%d,
                 "service":{"id":"gateway-service","name":"%s","external_id":"%s"},
                 "route":{"id":"gateway-route","name":"%s","external_id":"%s"},
                 "request":{"id":"%s","method":"GET","path":"/orders?secret=not-retained"},
                 "response":{"status":%s},"upstream_status":"%s",
                 "latencies_ms":{"request":12,"gateway":2,"proxy":10}}
                """.formatted(eventId, provider, System.currentTimeMillis(), service, id, route, id, UUID.randomUUID(), upstream, upstream);
    }
}
