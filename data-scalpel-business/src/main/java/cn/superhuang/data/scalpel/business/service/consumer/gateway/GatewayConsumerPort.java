package cn.superhuang.data.scalpel.business.service.consumer.gateway;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;

public interface GatewayConsumerPort {

    GatewayProvider provider();

    GatewayConsumerResult upsert(GatewayConsumerSpec consumer);

    void remove(GatewayConsumerReference consumer);

    GatewayInspectionResult inspect(GatewayConsumerInspectionSpec consumer);
}
