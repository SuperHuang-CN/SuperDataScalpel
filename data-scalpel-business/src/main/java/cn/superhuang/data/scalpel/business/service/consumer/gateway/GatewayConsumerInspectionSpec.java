package cn.superhuang.data.scalpel.business.service.consumer.gateway;

public record GatewayConsumerInspectionSpec(
        GatewayConsumerSpec expected,
        GatewayConsumerReference reference,
        boolean expectedPresent
) {

    public GatewayConsumerInspectionSpec {
        if (expected == null) throw new IllegalArgumentException("Expected gateway consumer is required");
        if (reference == null) throw new IllegalArgumentException("Gateway consumer reference is required");
    }
}
