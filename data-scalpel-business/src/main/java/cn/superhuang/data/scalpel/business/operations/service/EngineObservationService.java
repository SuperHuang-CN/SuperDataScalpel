package cn.superhuang.data.scalpel.business.operations.service;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.business.operations.repository.EngineObservationRepository;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineRegistrationState;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineRuntimeService;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeEngineRuntimeOverviewResponse;
import cn.superhuang.data.scalpel.contract.execution.DispatcherRuntimeDependency;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;

@Service
public class EngineObservationService {
    private static final Set<String> REQUIRED = Set.of("backend", "artifact-storage", "kafka", "kafka-listeners");
    private final ComputeEngineRepository engines;
    private final EngineObservationRepository observations;
    private final ComputeEngineRuntimeService runtime;
    private final OperationsProperties properties;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public EngineObservationService(ComputeEngineRepository engines, EngineObservationRepository observations,
                                    ComputeEngineRuntimeService runtime, OperationsProperties properties,
                                    ObjectMapper json, PlatformTransactionManager manager) {
        this.engines = engines; this.observations = observations; this.runtime = runtime; this.properties = properties;
        this.json = json; tx = new TransactionTemplate(manager);
    }
    public void observe(UUID id) {
        UUID token = tx.execute(status -> claim(id));
        if (token == null) return;
        ComputeEngineRuntimeOverviewResponse snapshot = null;
        try { snapshot = runtime.overview(id); } catch (RuntimeException ignored) {
            // Only a stable, safe diagnostic is persisted; the source client owns transport diagnostics.
        }
        var result = snapshot;
        tx.executeWithoutResult(status -> complete(id, token, result));
    }
    private UUID claim(UUID id) {
        var e = engines.findByIdForUpdate(id).orElse(null);
        if (e == null || e.getRegistrationState() != ComputeEngineRegistrationState.ACTIVE) return null;
        var o = observations.findByEngineId(id).orElseGet(EngineObservation::new);
        Instant now = Instant.now();
        if (o.getLeaseUntil() != null && o.getLeaseUntil().isAfter(now)) return null;
        if (o.getAttemptedAt() != null && o.getAttemptedAt().plusSeconds(30).isAfter(now)) return null;
        if (!Objects.equals(e.getUpdatedAt(), o.getEngineConfigurationAt())) {
            o.setState(EngineObservationState.UNKNOWN); o.setDependenciesReady(null);
            o.setReachableSamples(0); o.setReadySamples(0); o.setUnreachableSince(null); o.setNotReadySince(null);
        }
        o.setEngineId(id); o.setAttemptedAt(now); o.setEngineConfigurationAt(e.getUpdatedAt());
        o.setClaimToken(UUID.randomUUID()); o.setLeaseUntil(now.plusSeconds(60)); observations.save(o); return o.getClaimToken();
    }
    private void complete(UUID id, UUID token, ComputeEngineRuntimeOverviewResponse snapshot) {
        var e = engines.findByIdForUpdate(id).orElse(null);
        var o = observations.findByEngineId(id).orElse(null);
        if (o == null || !token.equals(o.getClaimToken())) return;
        o.setLeaseUntil(null); o.setClaimToken(null);
        Instant now = Instant.now();
        boolean adjacent = o.getObservedAt() != null && !o.getObservedAt().plus(properties.observationStaleAfter()).isBefore(now);
        if (e == null || e.getRegistrationState() != ComputeEngineRegistrationState.ACTIVE
                || !Objects.equals(e.getUpdatedAt(), o.getEngineConfigurationAt())
                || snapshot != null && (snapshot.collectedAt() == null || snapshot.collectedAt().plus(properties.observationStaleAfter()).isBefore(now)
                    || snapshot.collectedAt().isAfter(now.plusSeconds(30)))) {
            o.setState(EngineObservationState.UNKNOWN); o.setDependenciesReady(null);
            o.setReachableSamples(0); o.setReadySamples(0); o.setUnreachableSince(null); o.setNotReadySince(null);
            o.setSummary("配置已变化或观测结果不可用"); return;
        }
        o.setObservedAt(now);
        if (snapshot == null) {
            if (!adjacent || o.getState() != EngineObservationState.UNREACHABLE) o.setUnreachableSince(now);
            o.setState(EngineObservationState.UNREACHABLE); o.setDependenciesReady(null); o.setReachableSamples(0);
            o.setReadySamples(0); o.setNotReadySince(null); o.setSummary("无法取得有效的 Dispatcher 运行响应"); return;
        }
        var dependencyStates = new HashMap<String, String>();
        snapshot.dependencies().forEach(d -> dependencyStates.put(d.name(), d.state()));
        Boolean ready = REQUIRED.stream().anyMatch(n -> "DOWN".equals(dependencyStates.get(n))) ? Boolean.FALSE
                : REQUIRED.stream().allMatch(n -> "UP".equals(dependencyStates.get(n))) ? Boolean.TRUE : null;
        if (Boolean.FALSE.equals(ready)) {
            if (!adjacent || !Boolean.FALSE.equals(o.getDependenciesReady())) o.setNotReadySince(now);
        } else o.setNotReadySince(null);
        o.setReachableSamples(adjacent && o.getState() == EngineObservationState.REACHABLE ? Math.min(2, o.getReachableSamples() + 1) : 1);
        o.setReadySamples(Boolean.TRUE.equals(ready) ? adjacent && Boolean.TRUE.equals(o.getDependenciesReady()) ? Math.min(2, o.getReadySamples() + 1) : 1 : 0);
        o.setState(EngineObservationState.REACHABLE); o.setDependenciesReady(ready); o.setUnreachableSince(null);
        if (Boolean.TRUE.equals(ready)) o.setLastHealthyAt(now);
        o.setSummary(ready == null ? "必需依赖的观测不完整" : ready ? "引擎及必需依赖正常" : "引擎可达，必需依赖未就绪");
        o.setSnapshotJson(json.writeValueAsString(new ComputeEngineRuntimeOverviewResponse(snapshot.engineId(), snapshot.dispatcherInstanceId(),
                snapshot.backendType(), snapshot.version(), snapshot.dispatcherRegistrationState(),
                snapshot.dependencies().stream().map(d -> new DispatcherRuntimeDependency(d.name(), d.state(), null)).toList(),
                snapshot.admissionCapacity(), snapshot.admissionUsage(), null, snapshot.collectedAt())));
    }
}
