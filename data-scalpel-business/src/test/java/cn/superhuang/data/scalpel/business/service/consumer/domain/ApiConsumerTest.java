package cn.superhuang.data.scalpel.business.service.consumer.domain;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiConsumerTest {

    @Test
    void normalizesStableCodeAndIncrementsRevisionOnlyThroughProfileUpdate() {
        ApiConsumer consumer = ApiConsumer.create("  Customer.Api  ", "  客户系统  ", "  初始说明  ");

        assertEquals("customer.api", consumer.getCode());
        assertEquals("客户系统", consumer.getName());
        assertEquals("初始说明", consumer.getDescription());
        assertEquals(1, consumer.getRevision());

        consumer.update("新客户系统", " ");

        assertEquals("customer.api", consumer.getCode());
        assertEquals("新客户系统", consumer.getName());
        assertNull(consumer.getDescription());
        assertEquals(2, consumer.getRevision());
    }

    @Test
    void rejectsInvalidCodes() {
        assertThrows(IllegalArgumentException.class, () -> ApiConsumer.create("a", "消费者", null));
        assertThrows(IllegalArgumentException.class, () -> ApiConsumer.create("1consumer", "消费者", null));
        assertThrows(IllegalArgumentException.class, () -> ApiConsumer.create("consumer/one", "消费者", null));
        assertThrows(IllegalArgumentException.class, () -> ApiConsumer.create("a".repeat(65), "消费者", null));
    }

    @Test
    void tracksSynchronizationAndDeleteFailuresWithoutUnboundedErrors() {
        GatewayConsumerBinding binding = GatewayConsumerBinding.pending(UUID.randomUUID(), GatewayProvider.KONG);
        binding.beginSync();
        Instant operationStartedAt = binding.getOperationStartedAt();

        assertEquals(GatewayConsumerSyncStatus.SYNC_PENDING, binding.getSyncStatus());
        assertTrue(binding.hasRecentOperation(operationStartedAt.plusSeconds(1), Duration.ofSeconds(30)));
        assertFalse(binding.hasRecentOperation(operationStartedAt.plusSeconds(31), Duration.ofSeconds(30)));

        binding.syncFailed("x".repeat(1200));
        assertEquals(GatewayConsumerSyncStatus.SYNC_FAILED, binding.getSyncStatus());
        assertEquals(1000, binding.getLastError().length());
        assertNull(binding.getOperationStartedAt());

        binding.beginSync();
        binding.synchronizedWith("kong-consumer-id", 3);
        assertEquals(GatewayConsumerSyncStatus.SYNCED, binding.getSyncStatus());
        assertEquals("kong-consumer-id", binding.getExternalId());
        assertEquals(3, binding.getSyncedRevision());
        assertNull(binding.getLastError());

        binding.beginDelete();
        binding.deleteFailed(null);
        assertEquals(GatewayConsumerSyncStatus.DELETE_FAILED, binding.getSyncStatus());
        assertEquals("网关消费者删除失败", binding.getLastError());
    }
}
