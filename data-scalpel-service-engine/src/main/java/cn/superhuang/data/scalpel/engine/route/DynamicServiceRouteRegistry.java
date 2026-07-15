package cn.superhuang.data.scalpel.engine.route;

import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentRequest;
import cn.superhuang.data.scalpel.engine.deployment.StoredServiceDeployment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Maintains live Spring MVC mappings and the immutable deployment snapshot behind each one. */
@Component
public class DynamicServiceRouteRegistry {

    private final RequestMappingHandlerMapping handlerMapping;
    private final ObjectProvider<StandardServiceQueryHandler> handlerProvider;
    private final Method handlerMethod;
    private final Map<UUID, RequestMappingInfo> mappingsByServiceId = new HashMap<>();
    private final Map<String, UUID> serviceIdByPath = new HashMap<>();
    private final Map<String, StoredServiceDeployment> deploymentByPath = new HashMap<>();

    public DynamicServiceRouteRegistry(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            ObjectProvider<StandardServiceQueryHandler> handlerProvider
    ) {
        this.handlerMapping = handlerMapping;
        this.handlerProvider = handlerProvider;
        try {
            this.handlerMethod = StandardServiceQueryHandler.class.getMethod(
                    "execute", jakarta.servlet.http.HttpServletRequest.class,
                    cn.superhuang.data.scalpel.contract.service.StandardServiceQueryRequest.class
            );
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("标准服务处理方法不存在", exception);
        }
    }

    public synchronized void validate(ServiceDeploymentRequest request) {
        String path = normalize(request.routePath());
        UUID occupiedBy = serviceIdByPath.get(path);
        if (occupiedBy != null && !occupiedBy.equals(request.serviceId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务路径已被其他已部署服务占用：" + path);
        }
    }

    public synchronized void register(StoredServiceDeployment deployment) {
        ServiceDeploymentRequest request = deployment.request();
        String path = normalize(request.routePath());
        validate(request);
        UUID serviceId = request.serviceId();
        RequestMappingInfo existingMapping = mappingsByServiceId.get(serviceId);
        String currentPath = existingMapping == null ? null : pathFor(serviceId);
        if (!path.equals(currentPath)) {
            RequestMappingInfo mapping = mapping(path);
            try {
                handlerMapping.registerMapping(mapping, handlerProvider.getObject(), handlerMethod);
            } catch (IllegalStateException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "服务路径与系统现有路由冲突：" + path, exception);
            }
            if (existingMapping != null) {
                handlerMapping.unregisterMapping(existingMapping);
                if (currentPath != null) {
                    serviceIdByPath.remove(currentPath);
                    deploymentByPath.remove(currentPath);
                }
            }
            mappingsByServiceId.put(serviceId, mapping);
        }
        serviceIdByPath.put(path, serviceId);
        deploymentByPath.put(path, deployment);
    }

    public synchronized void unregister(UUID serviceId) {
        RequestMappingInfo mapping = mappingsByServiceId.remove(serviceId);
        if (mapping != null) {
            handlerMapping.unregisterMapping(mapping);
        }
        String path = pathFor(serviceId);
        if (path != null) {
            serviceIdByPath.remove(path);
            deploymentByPath.remove(path);
        }
    }

    public synchronized Optional<StoredServiceDeployment> deployment(String path) {
        try {
            return Optional.ofNullable(deploymentByPath.get(EngineRoutePath.normalize(path)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static String normalize(String path) {
        try {
            return EngineRoutePath.normalize(path);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private String pathFor(UUID serviceId) {
        return serviceIdByPath.entrySet().stream()
                .filter(entry -> entry.getValue().equals(serviceId))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    private static RequestMappingInfo mapping(String path) {
        return RequestMappingInfo.paths(path).methods(RequestMethod.POST).build();
    }
}
