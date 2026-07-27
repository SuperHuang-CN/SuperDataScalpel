package cn.superhuang.data.scalpel.business.service.gateway.service;

import java.util.UUID;

public record GatewayServiceReference(
        UUID id,
        String code,
        String externalServiceId,
        String externalRouteId
) {
}
