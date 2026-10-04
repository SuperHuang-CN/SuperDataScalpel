package cn.superhuang.data.scalpel.engine.cluster;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("engineRuntime")
public class EngineRuntimeHealthIndicator implements HealthIndicator {
    private final EngineClusterCoordinator coordinator;
    public EngineRuntimeHealthIndicator(org.springframework.beans.factory.ObjectProvider<EngineClusterCoordinator> coordinator){this.coordinator=coordinator.getIfAvailable();}
    @Override public Health health(){return coordinator==null || coordinator.ready()?Health.up().build():Health.outOfService().build();}
}
