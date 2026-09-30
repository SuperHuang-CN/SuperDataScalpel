package cn.superhuang.superapigateway.runtime;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class AccessValidityTest {
    @Test void windowIncludesStartButExcludesExpiry() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        var validity = new AccessValidity(start, start.plusSeconds(1), 5);
        assertThat(validity.validAt(start.minusNanos(1))).isFalse();
        assertThat(validity.validAt(start)).isTrue();
        assertThat(validity.validAt(start.plusSeconds(1))).isFalse();
    }
    @Test void expiredKeyAndSubscriptionAreRejectedEvenInAnInstalledSnapshot() {
        UUID consumer = UUID.randomUUID(), service = UUID.randomUUID();
        var expired = new AccessValidity(null, Instant.now().minusSeconds(1), 10);
        var snapshot = new GatewayRuntimeSnapshot(1, Map.of(),
                Map.of("key", new GatewayRuntimeSnapshot.RuntimeConsumer(consumer, "consumer", true, null, expired)),
                Map.of(new GatewayRuntimeSnapshot.SubscriptionKey(consumer, service), expired));
        assertThat(snapshot.consumer("key")).isNull();
        assertThat(snapshot.subscribed(consumer, service)).isFalse();
        assertThat(snapshot.subscriptionRate(consumer, service)).isEqualTo(10);
    }
}
