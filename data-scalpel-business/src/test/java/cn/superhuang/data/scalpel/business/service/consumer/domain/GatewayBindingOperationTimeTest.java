package cn.superhuang.data.scalpel.business.service.consumer.domain;

import cn.superhuang.data.scalpel.business.service.consumer.credential.domain.GatewayCredentialBinding;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.GatewaySubscriptionBinding;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class GatewayBindingOperationTimeTest {
    @Test
    void operationIdentitySurvivesDatabaseMicrosecondPrecisionForAllConsumerBindings() {
        Instant clockValue = Instant.parse("2026-10-04T01:02:03.123456789Z");
        Instant storedValue = Instant.parse("2026-10-04T01:02:03.123456Z");
        var consumer = GatewayConsumerBinding.pending(UUID.randomUUID(), GatewayProvider.DATASCALPEL);
        var credential = GatewayCredentialBinding.pending(UUID.randomUUID(), GatewayProvider.DATASCALPEL);
        var subscription = GatewaySubscriptionBinding.pending(UUID.randomUUID(), GatewayProvider.DATASCALPEL);
        try (var clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(clockValue);
            consumer.beginSync();
            assertEquals(storedValue, consumer.getOperationStartedAt());
            consumer.beginDelete();
            assertEquals(storedValue, consumer.getOperationStartedAt());
            credential.beginSync();
            assertEquals(storedValue, credential.getOperationStartedAt());
            credential.beginDelete();
            assertEquals(storedValue, credential.getOperationStartedAt());
            subscription.beginGrant();
            assertEquals(storedValue, subscription.getOperationStartedAt());
            subscription.beginRevoke();
            assertEquals(storedValue, subscription.getOperationStartedAt());
        }
    }
}
