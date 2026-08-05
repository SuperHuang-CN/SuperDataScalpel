package cn.superhuang.superapigateway.runtime;

import cn.superhuang.superapigateway.controlplane.domain.GatewayInstanceEntity;
import cn.superhuang.superapigateway.controlplane.repository.GatewayInstanceRepository;
import cn.superhuang.superapigateway.controlplane.service.ConfigurationRevisionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class RuntimePersistenceService {

    private final GatewayInstanceRepository instances;
    private final ConfigurationRevisionService revisions;

    public RuntimePersistenceService(
            GatewayInstanceRepository instances,
            ConfigurationRevisionService revisions
    ) {
        this.instances = instances;
        this.revisions = revisions;
    }

    @Transactional(readOnly = true)
    public long currentRevision() {
        return revisions.currentRevision();
    }

    @Transactional
    public void heartbeat(
            String instanceId,
            Instant startedAt,
            String state,
            long loadedRevision,
            String lastError,
            String applicationVersion
    ) {
        GatewayInstanceEntity entity = instances.findById(instanceId)
                .orElseGet(() -> new GatewayInstanceEntity(instanceId, startedAt));
        entity.heartbeat(state, loadedRevision, lastError, applicationVersion);
        instances.save(entity);
    }
}
