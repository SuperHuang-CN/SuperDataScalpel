package cn.superhuang.data.scalpel.engine.deployment;

import cn.superhuang.data.scalpel.contract.service.EngineDeploymentStatus;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceUndeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.StandardServiceDefinition;
import cn.superhuang.data.scalpel.engine.config.EngineProperties;
import cn.superhuang.data.scalpel.engine.datasource.EngineDataSourceStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/** Idempotent persistence boundary for deployed service snapshots. */
@Service
public class EngineDeploymentStore {

    private final EngineDeploymentRepository repository;
    private final EngineProperties properties;
    private final EngineDataSourceStore dataSourceStore;
    private final ObjectMapper objectMapper;

    public EngineDeploymentStore(
            EngineDeploymentRepository repository,
            EngineProperties properties,
            EngineDataSourceStore dataSourceStore,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.properties = properties;
        this.dataSourceStore = dataSourceStore;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ServiceDeploymentResponse deploy(ServiceDeploymentRequest request) {
        dataSourceStore.requireSnapshot(request.dataSourceId());
        EngineDeployment deployment = repository.findByEngineCodeAndServiceId(properties.code(), request.serviceId())
                .orElse(null);
        if (deployment != null && deployment.getRevision() > request.revision()) {
            return response(deployment, "已存在更新版本的部署");
        }
        if (deployment != null && deployment.getRevision() == request.revision()) {
            if (!deployment.getDefinitionDigest().equals(request.definitionDigest())
                    || !deployment.getDataSourceId().equals(request.dataSourceId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "同一服务版本的部署摘要不一致");
            }
            return response(deployment, "部署已存在");
        }

        String definitionJson = write(request.definition());
        if (deployment == null) {
            deployment = EngineDeployment.create(
                    properties.code(), request.serviceId(), request.revision(), request.serviceCode(), request.routePath(),
                    request.definitionDigest(), definitionJson, request.dataSourceId()
            );
        } else {
            deployment.apply(
                    request.revision(), request.serviceCode(), request.routePath(), request.definitionDigest(),
                    definitionJson, request.dataSourceId()
            );
        }
        repository.save(deployment);
        return response(deployment, "部署已保存");
    }

    @Transactional
    public ServiceDeploymentResponse remove(ServiceUndeploymentRequest request) {
        EngineDeployment deployment = repository.findByEngineCodeAndServiceId(properties.code(), request.serviceId())
                .orElse(null);
        if (deployment == null) {
            return new ServiceDeploymentResponse(request.serviceId(), request.revision(), EngineDeploymentStatus.REMOVED, "部署不存在");
        }
        if (deployment.getRevision() > request.revision()) {
            return new ServiceDeploymentResponse(
                    deployment.getServiceId(), deployment.getRevision(), toStatus(deployment.getStatus()), "已存在更新版本的部署"
            );
        }
        if (deployment.getStatus() == EngineDeploymentRecordStatus.REMOVED && deployment.getRevision() == request.revision()) {
            return new ServiceDeploymentResponse(request.serviceId(), request.revision(), EngineDeploymentStatus.REMOVED, "部署已移除");
        }
        deployment.markRemoved(request.revision());
        return new ServiceDeploymentResponse(request.serviceId(), request.revision(), EngineDeploymentStatus.REMOVED, "部署已移除");
    }

    @Transactional(readOnly = true)
    public List<StoredServiceDeployment> activeDeployments() {
        return repository.findAllByEngineCodeAndStatus(properties.code(), EngineDeploymentRecordStatus.DEPLOYED)
                .stream().map(this::read).toList();
    }

    @Transactional(readOnly = true)
    public StoredServiceDeployment activeDeployment(java.util.UUID serviceId) {
        EngineDeployment deployment = repository.findByEngineCodeAndServiceId(properties.code(), serviceId)
                .orElseThrow(() -> new IllegalStateException("服务部署不存在"));
        if (deployment.getStatus() != EngineDeploymentRecordStatus.DEPLOYED) {
            throw new IllegalStateException("服务当前未部署");
        }
        return read(deployment);
    }

    private ServiceDeploymentResponse response(EngineDeployment deployment, String message) {
        return new ServiceDeploymentResponse(
                deployment.getServiceId(), deployment.getRevision(), toStatus(deployment.getStatus()), message
        );
    }

    private EngineDeploymentStatus toStatus(EngineDeploymentRecordStatus status) {
        return status == EngineDeploymentRecordStatus.DEPLOYED
                ? EngineDeploymentStatus.DEPLOYED
                : EngineDeploymentStatus.REMOVED;
    }

    private StoredServiceDeployment read(EngineDeployment deployment) {
        StandardServiceDefinition definition = read(deployment.getDefinitionJson(), StandardServiceDefinition.class);
        return new StoredServiceDeployment(new ServiceDeploymentRequest(
                deployment.getServiceId(), deployment.getRevision(), deployment.getServiceCode(), deployment.getRoutePath(),
                deployment.getDefinitionDigest(), definition, deployment.getDataSourceId()
        ));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化 Engine 部署快照", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法读取 Engine 部署快照", exception);
        }
    }
}
