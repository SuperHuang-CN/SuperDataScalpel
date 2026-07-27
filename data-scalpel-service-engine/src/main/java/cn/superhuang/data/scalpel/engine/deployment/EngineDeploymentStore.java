package cn.superhuang.data.scalpel.engine.deployment;

import cn.superhuang.data.scalpel.contract.service.EngineDeploymentStatus;
import cn.superhuang.data.scalpel.contract.service.ServiceDefinitionSnapshot;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceUndeploymentRequest;
import cn.superhuang.data.scalpel.engine.config.EngineProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** Transactional persistence boundary for the Engine deployment state machine. */
@Service
public class EngineDeploymentStore {

    private static final EnumSet<EngineDeploymentRecordStatus> DEPLOYMENT_RECOVERY_STATUSES = EnumSet.of(
            EngineDeploymentRecordStatus.DEPLOYING,
            EngineDeploymentRecordStatus.DEPLOYED
    );
    private static final EnumSet<EngineDeploymentRecordStatus> REMOVAL_RECOVERY_STATUSES = EnumSet.of(
            EngineDeploymentRecordStatus.REMOVING,
            EngineDeploymentRecordStatus.REMOVE_FAILED
    );

    private final EngineDeploymentRepository repository;
    private final EngineProperties properties;
    private final ObjectMapper objectMapper;

    public EngineDeploymentStore(
            EngineDeploymentRepository repository,
            EngineProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DeploymentPreparation beginDeployment(ServiceDeploymentRequest request) {
        EngineDeployment deployment = repository.findByEngineCodeAndServiceId(properties.code(), request.serviceId())
                .orElse(null);
        if (deployment != null && deployment.getRevision() > request.revision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已存在更新版本的服务部署");
        }
        if (deployment != null && deployment.getRevision() == request.revision()) {
            requireSameSnapshot(deployment, request);
            if (deployment.getStatus() == EngineDeploymentRecordStatus.DEPLOYED) {
                return new DeploymentPreparation(null, response(
                        deployment, EngineDeploymentStatus.DEPLOYED, "部署已存在"
                ));
            }
        }

        String definitionJson = write(request.definition());
        if (deployment == null) {
            deployment = EngineDeployment.create(
                    properties.code(), request.serviceId(), request.revision(), request.serviceCode(), request.routePath(),
                    request.definitionDigest(), definitionJson, request.dataSourceId()
            );
        } else {
            deployment.beginDeployment(
                    request.revision(), request.serviceCode(), request.routePath(), request.definitionDigest(),
                    definitionJson, request.dataSourceId()
            );
        }
        repository.saveAndFlush(deployment);
        return new DeploymentPreparation(read(deployment), null);
    }

    @Transactional
    public ServiceDeploymentResponse completeDeployment(UUID serviceId, long revision) {
        EngineDeployment deployment = requireCurrent(serviceId, revision);
        deployment.deployed();
        repository.flush();
        return response(deployment, EngineDeploymentStatus.DEPLOYED, "部署成功");
    }

    @Transactional
    public void failDeployment(UUID serviceId, long revision, String message) {
        EngineDeployment deployment = current(serviceId, revision);
        if (deployment != null) {
            deployment.deploymentFailed(message);
            repository.flush();
        }
    }

    @Transactional
    public RemovalPreparation beginRemoval(ServiceUndeploymentRequest request) {
        EngineDeployment deployment = repository.findByEngineCodeAndServiceId(properties.code(), request.serviceId())
                .orElse(null);
        if (deployment == null) {
            return new RemovalPreparation(false, new ServiceDeploymentResponse(
                    request.serviceId(), request.revision(), EngineDeploymentStatus.REMOVED, "部署不存在"
            ));
        }
        if (deployment.getRevision() > request.revision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已存在更新版本的服务部署");
        }
        if (deployment.getRevision() == request.revision()
                && deployment.getStatus() == EngineDeploymentRecordStatus.REMOVED) {
            return new RemovalPreparation(false, response(
                    deployment, EngineDeploymentStatus.REMOVED, "部署已移除"
            ));
        }
        deployment.beginRemoval(request.revision());
        repository.flush();
        return new RemovalPreparation(true, null);
    }

    @Transactional
    public ServiceDeploymentResponse completeRemoval(UUID serviceId, long revision) {
        EngineDeployment deployment = requireCurrent(serviceId, revision);
        deployment.removed(revision);
        repository.flush();
        return response(deployment, EngineDeploymentStatus.REMOVED, "部署已移除");
    }

    @Transactional
    public void failRemoval(UUID serviceId, long revision, String message) {
        EngineDeployment deployment = current(serviceId, revision);
        if (deployment != null) {
            deployment.removalFailed(message);
            repository.flush();
        }
    }

    @Transactional(readOnly = true)
    public List<StoredServiceDeployment> recoverableDeployments() {
        return repository.findAllByEngineCodeAndStatusIn(properties.code(), DEPLOYMENT_RECOVERY_STATUSES)
                .stream().map(this::read).toList();
    }

    @Transactional(readOnly = true)
    public List<StoredServiceDeployment> recoverableRemovals() {
        return repository.findAllByEngineCodeAndStatusIn(properties.code(), REMOVAL_RECOVERY_STATUSES)
                .stream().map(this::read).toList();
    }

    private EngineDeployment requireCurrent(UUID serviceId, long revision) {
        EngineDeployment deployment = current(serviceId, revision);
        if (deployment == null) {
            throw new IllegalStateException("服务部署版本已发生变化");
        }
        return deployment;
    }

    private EngineDeployment current(UUID serviceId, long revision) {
        return repository.findByEngineCodeAndServiceId(properties.code(), serviceId)
                .filter(deployment -> deployment.getRevision() == revision)
                .orElse(null);
    }

    private static void requireSameSnapshot(EngineDeployment deployment, ServiceDeploymentRequest request) {
        if (!deployment.getDefinitionDigest().equals(request.definitionDigest())
                || !deployment.getDataSourceId().equals(request.dataSourceId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同一服务版本的部署摘要不一致");
        }
    }

    private static ServiceDeploymentResponse response(
            EngineDeployment deployment,
            EngineDeploymentStatus status,
            String message
    ) {
        return new ServiceDeploymentResponse(
                deployment.getServiceId(), deployment.getRevision(), status, message
        );
    }

    private StoredServiceDeployment read(EngineDeployment deployment) {
        ServiceDefinitionSnapshot definition = read(deployment.getDefinitionJson(), ServiceDefinitionSnapshot.class);
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

    public record DeploymentPreparation(
            StoredServiceDeployment deployment,
            ServiceDeploymentResponse completedResponse
    ) {
    }

    public record RemovalPreparation(
            boolean removalRequired,
            ServiceDeploymentResponse completedResponse
    ) {
    }
}
