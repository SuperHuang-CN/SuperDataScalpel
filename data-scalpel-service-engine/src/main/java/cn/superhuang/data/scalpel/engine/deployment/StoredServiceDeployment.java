package cn.superhuang.data.scalpel.engine.deployment;

import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentRequest;

/** Decrypted deployment in memory, ready for route registration and query execution. */
public record StoredServiceDeployment(ServiceDeploymentRequest request, long generation, EngineDeploymentRecordStatus status) {
    public StoredServiceDeployment(ServiceDeploymentRequest request) { this(request,0,EngineDeploymentRecordStatus.DEPLOYED); }
}
