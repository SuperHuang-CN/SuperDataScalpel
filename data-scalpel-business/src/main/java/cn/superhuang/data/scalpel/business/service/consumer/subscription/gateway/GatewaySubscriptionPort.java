package cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;

public interface GatewaySubscriptionPort {

    GatewayProvider provider();

    GatewaySubscriptionResult grant(GatewaySubscriptionSpec subscription);

    void revoke(GatewaySubscriptionReference subscription);

    GatewayInspectionResult inspect(GatewaySubscriptionInspectionSpec subscription);
}
