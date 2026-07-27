package cn.superhuang.data.scalpel.business.service.gateway.service;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;

public interface GatewayServicePort {

    GatewayProvider provider();

    GatewayServiceResult publish(GatewayServiceSpec service);

    void remove(GatewayServiceReference service);

    GatewayInspectionResult inspect(GatewayServiceInspectionSpec service);
}
