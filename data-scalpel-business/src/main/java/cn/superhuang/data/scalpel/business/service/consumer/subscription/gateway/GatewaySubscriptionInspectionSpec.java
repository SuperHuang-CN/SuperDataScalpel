package cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway;

public record GatewaySubscriptionInspectionSpec(
        GatewaySubscriptionSpec expected,
        String externalMembershipId,
        boolean expectedPresent
) {

    public GatewaySubscriptionInspectionSpec {
        if (expected == null) throw new IllegalArgumentException("Expected gateway subscription is required");
    }
}
