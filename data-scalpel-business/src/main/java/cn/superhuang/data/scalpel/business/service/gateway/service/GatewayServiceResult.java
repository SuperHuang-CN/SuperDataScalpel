package cn.superhuang.data.scalpel.business.service.gateway.service;

public record GatewayServiceResult(
        String externalServiceId,
        String externalRouteId,
        String gatewayUrl
) {
}
