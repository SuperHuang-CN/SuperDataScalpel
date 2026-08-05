package cn.superhuang.data.scalpel.business.service.accesslog.service;

import cn.superhuang.data.scalpel.business.service.accesslog.domain.GatewayAccessIdentityResolutionStatus;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;

import java.time.Instant;
import java.util.UUID;

record GatewayAccessRecord(
        UUID eventId,
        String schemaVersion,
        GatewayProvider gatewayProvider,
        Instant occurredAt,
        Instant observedAt,
        Instant receivedAt,
        UUID dataServiceId,
        String gatewayServiceId,
        String gatewayServiceName,
        String gatewayRouteId,
        String gatewayRouteName,
        UUID consumerId,
        String gatewayConsumerId,
        String consumerCode,
        String gatewayCredentialExternalId,
        String gatewayRequestId,
        String requestMethod,
        String requestPath,
        int responseStatus,
        String upstreamStatus,
        Long requestSizeBytes,
        Long responseSizeBytes,
        Long requestLatencyMs,
        Long kongLatencyMs,
        Long proxyLatencyMs,
        Long receiveLatencyMs,
        String clientIp,
        GatewayAccessIdentityResolutionStatus identityResolutionStatus,
        boolean gatewayRejected,
        boolean gatewayError,
        boolean upstreamError,
        String kafkaTopic,
        int kafkaPartition,
        long kafkaOffset
) {
}
