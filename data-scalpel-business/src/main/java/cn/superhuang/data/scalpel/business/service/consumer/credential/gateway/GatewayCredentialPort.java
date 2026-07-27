package cn.superhuang.data.scalpel.business.service.consumer.credential.gateway;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;

public interface GatewayCredentialPort {

    GatewayProvider provider();

    GatewayCredentialResult upsert(GatewayCredentialSpec credential);

    void remove(GatewayCredentialReference credential);

    GatewayInspectionResult inspect(GatewayCredentialInspectionSpec credential);
}
