package cn.superhuang.data.scalpel.engine.deployment;

import cn.superhuang.data.scalpel.contract.service.EngineDeploymentStatus;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceUndeploymentRequest;
import cn.superhuang.data.scalpel.engine.datasource.EngineDataSourceStore;
import cn.superhuang.data.scalpel.engine.route.DynamicServiceRouteRegistry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** Coordinates durable deployment snapshots with live dynamic Spring MVC registrations. */
@Service
public class EngineRuntimeDeploymentService {

    private final EngineDeploymentStore store;
    private final EngineDataSourceStore dataSourceStore;
    private final DynamicServiceRouteRegistry routeRegistry;

    public EngineRuntimeDeploymentService(
            EngineDeploymentStore store,
            EngineDataSourceStore dataSourceStore,
            DynamicServiceRouteRegistry routeRegistry
    ) {
        this.store = store;
        this.dataSourceStore = dataSourceStore;
        this.routeRegistry = routeRegistry;
    }

    public ServiceDeploymentResponse deploy(ServiceDeploymentRequest request) {
        routeRegistry.validate(request);
        ServiceDeploymentResponse response = store.deploy(request);
        if (response.status() == EngineDeploymentStatus.DEPLOYED) {
            routeRegistry.register(store.activeDeployment(request.serviceId()));
        }
        return response;
    }

    public ServiceDeploymentResponse remove(ServiceUndeploymentRequest request) {
        ServiceDeploymentResponse response = store.remove(request);
        if (response.status() == EngineDeploymentStatus.REMOVED) {
            routeRegistry.unregister(request.serviceId());
        }
        return response;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreRoutes() {
        dataSourceStore.restore();
        store.activeDeployments().forEach(routeRegistry::register);
    }
}
