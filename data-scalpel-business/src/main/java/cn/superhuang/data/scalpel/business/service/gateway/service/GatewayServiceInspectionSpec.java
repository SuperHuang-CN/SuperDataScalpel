package cn.superhuang.data.scalpel.business.service.gateway.service;

public record GatewayServiceInspectionSpec(
        GatewayServiceSpec expected,
        GatewayServiceReference reference,
        boolean expectedPresent
) {

    public GatewayServiceInspectionSpec {
        if (expected == null) throw new IllegalArgumentException("Expected gateway service is required");
        if (reference == null) throw new IllegalArgumentException("Gateway service reference is required");
    }
}
