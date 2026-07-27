package cn.superhuang.data.scalpel.engine.deployment;

import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceUndeploymentRequest;
import cn.superhuang.data.scalpel.engine.datasource.EngineDataSourceStore;
import cn.superhuang.data.scalpel.engine.route.DynamicServiceRouteRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** Coordinates durable deployment state with live dynamic Spring MVC registrations. */
@Service
public class EngineRuntimeDeploymentService {

    private static final Logger log = LoggerFactory.getLogger(EngineRuntimeDeploymentService.class);

    private final EngineDeploymentStore store;
    private final EngineDeploymentValidator validator;
    private final EngineDataSourceStore dataSourceStore;
    private final DynamicServiceRouteRegistry routeRegistry;

    public EngineRuntimeDeploymentService(
            EngineDeploymentStore store,
            EngineDeploymentValidator validator,
            EngineDataSourceStore dataSourceStore,
            DynamicServiceRouteRegistry routeRegistry
    ) {
        this.store = store;
        this.validator = validator;
        this.dataSourceStore = dataSourceStore;
        this.routeRegistry = routeRegistry;
    }

    public ServiceDeploymentResponse deploy(ServiceDeploymentRequest request) {
        validator.validate(request);
        routeRegistry.validate(request);
        EngineDeploymentStore.DeploymentPreparation preparation = store.beginDeployment(request);
        if (preparation.completedResponse() != null) {
            return preparation.completedResponse();
        }
        try {
            routeRegistry.register(preparation.deployment());
            return store.completeDeployment(request.serviceId(), request.revision());
        } catch (RuntimeException exception) {
            routeRegistry.unregister(request.serviceId());
            store.failDeployment(request.serviceId(), request.revision(), exception.getMessage());
            throw exception;
        }
    }

    public ServiceDeploymentResponse remove(ServiceUndeploymentRequest request) {
        EngineDeploymentStore.RemovalPreparation preparation = store.beginRemoval(request);
        if (!preparation.removalRequired()) {
            return preparation.completedResponse();
        }
        try {
            routeRegistry.unregister(request.serviceId());
            return store.completeRemoval(request.serviceId(), request.revision());
        } catch (RuntimeException exception) {
            store.failRemoval(request.serviceId(), request.revision(), exception.getMessage());
            throw exception;
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreRoutes() {
        dataSourceStore.restore();
        store.recoverableRemovals().forEach(this::recoverRemoval);
        store.recoverableDeployments().forEach(this::recoverDeployment);
    }

    private void recoverDeployment(StoredServiceDeployment deployment) {
        ServiceDeploymentRequest request = deployment.request();
        try {
            validator.validate(request);
            routeRegistry.validate(request);
            routeRegistry.register(deployment);
            store.completeDeployment(request.serviceId(), request.revision());
        } catch (RuntimeException exception) {
            routeRegistry.unregister(request.serviceId());
            store.failDeployment(request.serviceId(), request.revision(), exception.getMessage());
            log.error("恢复数据服务部署失败，serviceId={}, revision={}",
                    request.serviceId(), request.revision(), exception);
        }
    }

    private void recoverRemoval(StoredServiceDeployment deployment) {
        ServiceDeploymentRequest request = deployment.request();
        try {
            routeRegistry.unregister(request.serviceId());
            store.completeRemoval(request.serviceId(), request.revision());
        } catch (RuntimeException exception) {
            store.failRemoval(request.serviceId(), request.revision(), exception.getMessage());
            log.error("恢复数据服务移除失败，serviceId={}, revision={}",
                    request.serviceId(), request.revision(), exception);
        }
    }
}
