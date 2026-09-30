package cn.superhuang.data.scalpel.business.service.gateway.domain;

import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class GatewayServiceBindingOperationTimeTest {

    @Test
    void publishTokenAlreadyHasDatabasePrecisionBeforeItIsCaptured() {
        Instant clockValue = Instant.parse("2026-09-29T06:25:36.524431789Z");
        Instant storedValue = Instant.parse("2026-09-29T06:25:36.524431Z");
        var binding = GatewayServiceBinding.publishing(UUID.randomUUID(), GatewayProvider.DATASCALPEL);
        try (var clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(clockValue);
            binding.beginPublish("/open-api/v1/example", DataServiceAccessMode.PUBLIC);
        }
        assertThat(binding.getOperationStartedAt()).isEqualTo(storedValue);
        binding.publishFailed("HTTP 401");
        assertThat(binding.getPublicationStatus()).isEqualTo(GatewayServicePublicationStatus.PUBLISH_FAILED);
        assertThat(binding.getOperationStartedAt()).isNull();
    }

    @Test
    void removalTokenAlreadyHasDatabasePrecisionBeforeItIsCaptured() {
        Instant clockValue = Instant.parse("2026-09-29T06:25:36.524431789Z");
        Instant storedValue = Instant.parse("2026-09-29T06:25:36.524431Z");
        var binding = GatewayServiceBinding.publishing(UUID.randomUUID(), GatewayProvider.DATASCALPEL);
        try (var clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(clockValue);
            binding.beginRemoval();
        }
        assertThat(binding.getOperationStartedAt()).isEqualTo(storedValue);
        binding.removalFailed("HTTP 503");
        assertThat(binding.getPublicationStatus()).isEqualTo(GatewayServicePublicationStatus.REMOVE_FAILED);
        assertThat(binding.getOperationStartedAt()).isNull();
    }
}
