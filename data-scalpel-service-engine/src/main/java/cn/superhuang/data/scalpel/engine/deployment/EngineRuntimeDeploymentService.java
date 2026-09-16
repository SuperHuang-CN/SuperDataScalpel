package cn.superhuang.data.scalpel.engine.deployment;

import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceUndeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import cn.superhuang.data.scalpel.engine.route.DynamicServiceRouteRegistry;
import cn.superhuang.data.scalpel.engine.script.EnginePublishedScriptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** Coordinates durable deployment state with live dynamic Spring MVC registrations. */
@Service
public class EngineRuntimeDeploymentService {

    private static final Logger log = LoggerFactory.getLogger(EngineRuntimeDeploymentService.class);

    // Bounded locks serialize an entire route/state operation without holding a DB transaction.
    // Engine code identifies one runtime; all mutations and startup recovery use these locks.
    private final Object[] operationLocks = java.util.stream.IntStream.range(0, 64)
            .mapToObj(ignored -> new Object()).toArray();

    private Object operationLock(java.util.UUID serviceId) {
        return operationLocks[Math.floorMod(serviceId.hashCode(), operationLocks.length)];
    }

    private final EngineDeploymentStore store;
    private final EngineDeploymentValidator validator;
    private final DynamicServiceRouteRegistry routeRegistry;
    private final EnginePublishedScriptService scriptService;

    public EngineRuntimeDeploymentService(
            EngineDeploymentStore store,
            EngineDeploymentValidator validator,
            DynamicServiceRouteRegistry routeRegistry,
            EnginePublishedScriptService scriptService
    ) {
        this.store = store;
        this.validator = validator;
        this.routeRegistry = routeRegistry;
        this.scriptService = scriptService;
    }

    public ServiceDeploymentResponse deploy(ServiceDeploymentRequest request) {
        synchronized (operationLock(request.serviceId())) {
            return deploySerially(request);
        }
    }

    private ServiceDeploymentResponse deploySerially(ServiceDeploymentRequest request) {
        validator.validate(request);
        routeRegistry.validate(request);
        EngineDeploymentStore.DeploymentPreparation preparation = store.beginDeployment(request);
        if (preparation.completedResponse() != null) {
            return preparation.completedResponse();
        }
        try {
            publish(preparation.deployment());
            return store.completeDeployment(request.serviceId());
        } catch (RuntimeException exception) {
            if (!isScript(request)) {
                routeRegistry.unregister(request.serviceId());
            }
            store.failDeployment(request.serviceId(), exception.getMessage());
            throw exception;
        }
    }

    public ServiceDeploymentResponse remove(ServiceUndeploymentRequest request) {
        synchronized (operationLock(request.serviceId())) {
            return removeSerially(request);
        }
    }

    private ServiceDeploymentResponse removeSerially(ServiceUndeploymentRequest request) {
        EngineDeploymentStore.RemovalPreparation preparation = store.beginRemoval(request);
        if (!preparation.removalRequired()) {
            return preparation.completedResponse();
        }
        try {
            removeRuntime(preparation.deployment());
            return store.completeRemoval(request.serviceId());
        } catch (RuntimeException exception) {
            store.failRemoval(request.serviceId(), exception.getMessage());
            throw exception;
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreRoutes() {
        store.recoverableRemovals().forEach(snapshot -> {
            synchronized (operationLock(snapshot.request().serviceId())) {
                store.recoverableRemoval(snapshot.request().serviceId()).ifPresent(this::recoverRemoval);
            }
        });
        store.recoverableDeployments().forEach(snapshot -> {
            synchronized (operationLock(snapshot.request().serviceId())) {
                store.recoverableDeployment(snapshot.request().serviceId()).ifPresent(this::recoverDeployment);
            }
        });
    }

    private void recoverDeployment(StoredServiceDeployment deployment) {
        ServiceDeploymentRequest request = deployment.request();
        try {
            validator.validate(request);
            routeRegistry.validate(request);
            publish(deployment);
            store.completeDeployment(request.serviceId());
        } catch (RuntimeException exception) {
            if (!isScript(request)) {
                routeRegistry.unregister(request.serviceId());
            }
            store.failDeployment(request.serviceId(), exception.getMessage());
            log.error("恢复数据服务部署失败，serviceId={}", request.serviceId(), exception);
        }
    }

    private void recoverRemoval(StoredServiceDeployment deployment) {
        ServiceDeploymentRequest request = deployment.request();
        try {
            removeRuntime(deployment);
            store.completeRemoval(request.serviceId());
        } catch (RuntimeException exception) {
            store.failRemoval(request.serviceId(), exception.getMessage());
            log.error("恢复数据服务移除失败，serviceId={}", request.serviceId(), exception);
        }
    }

    private void publish(StoredServiceDeployment deployment) {
        if (isScript(deployment.request())) {
            scriptService.upsert(deployment);
        } else {
            routeRegistry.register(deployment);
        }
    }

    private void removeRuntime(StoredServiceDeployment deployment) {
        if (deployment != null && isScript(deployment.request())) {
            scriptService.delete(deployment.request().serviceId());
        } else if (deployment != null) {
            routeRegistry.unregister(deployment.request().serviceId());
        }
    }

    private boolean isScript(ServiceDeploymentRequest request) {
        return request.definition().type() == DataServiceType.SCRIPT_API;
    }
}
