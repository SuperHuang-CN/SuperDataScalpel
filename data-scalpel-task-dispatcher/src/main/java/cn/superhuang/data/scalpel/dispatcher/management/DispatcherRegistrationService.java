package cn.superhuang.data.scalpel.dispatcher.management;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendReadiness;
import cn.superhuang.data.scalpel.contract.execution.DispatcherTargetDirectoryResponse;
import cn.superhuang.data.scalpel.dispatcher.backend.DispatcherBackendRegistry;
import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherArtifactService;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherProperties;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionState;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistrationState;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherRegistrationRepository;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.time.Instant;
import java.util.concurrent.locks.LockSupport;

@Service
public class DispatcherRegistrationService {
    private static final List<DispatcherExecutionState> ACTIVE_STATES = List.of(
            DispatcherExecutionState.QUEUED, DispatcherExecutionState.SUBMITTING,
            DispatcherExecutionState.SUBMITTED, DispatcherExecutionState.RUNNING,
            DispatcherExecutionState.CANCEL_REQUESTED);
    private final DispatcherRegistrationRepository repository;
    private final DispatcherTaskExecutionRepository executions;
    private final DispatcherIdentityService identity;
    private final DispatcherListenerManager listeners;
    private final DispatcherBackendRegistry backends;
    private final DispatcherArtifactService artifacts;
    private final DispatcherProperties properties;
    private final cn.superhuang.data.scalpel.dispatcher.config.DispatcherMessagingProperties messaging;
    private final cn.superhuang.data.scalpel.dispatcher.service.DispatcherEventService events;
    private final TransactionTemplate transaction;
    private final java.util.concurrent.ConcurrentHashMap<UUID, java.util.concurrent.locks.ReentrantLock> lifecycleLocks = new java.util.concurrent.ConcurrentHashMap<>();

    public DispatcherRegistrationService(DispatcherRegistrationRepository repository,
            DispatcherTaskExecutionRepository executions, DispatcherIdentityService identity,
            DispatcherListenerManager listeners, DispatcherBackendRegistry backends,
            DispatcherArtifactService artifacts, DispatcherProperties properties,
            PlatformTransactionManager transactionManager,
            cn.superhuang.data.scalpel.dispatcher.service.DispatcherEventService events,
            cn.superhuang.data.scalpel.dispatcher.config.DispatcherMessagingProperties messaging) {
        this.repository = repository;
        this.executions = executions;
        this.identity = identity;
        this.listeners = listeners;
        this.backends = backends;
        this.artifacts = artifacts;
        this.properties = properties;
        this.events = events;
        this.messaging = messaging;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public DispatcherTargetDirectoryResponse targets() {
        var registrations = repository.findAll();
        var targets = backends.all().stream().map(target -> {
            var readiness = backends.readiness(target);
            var registration = registrations.stream().filter(r -> Objects.equals(r.getTargetKey(), target.key())
                    && r.getState() != DispatcherRegistrationState.INACTIVE).findFirst().orElse(null);
            return new DispatcherTargetDirectoryResponse.Target(target.key(), target.name(), target.backend().type(),
                    target.fingerprint(), readiness.ready(), readiness.issues(),
                    registration == null ? null : registration.getEngineId(),
                    registration == null ? "UNREGISTERED" : registration.getState().name(),
                    new DispatcherTargetDirectoryResponse.Capabilities(true, true, true,
                            capabilities(target).streaming(), capabilities(target).durableCheckpoint()),
                    target.configuration().resourcePolicy(), readiness.checking());
        }).toList();
        return new DispatcherTargetDirectoryResponse(identity.instanceId().toString(), messaging.shared() ? 3 : 2,
                messaging.discovery(), targets);
    }

    public DispatcherInfoResponse info() { return info(null); }

    public DispatcherInfoResponse info(UUID engineId) {
        DispatcherRegistration registration = engineId == null ? legacyRegistration(false) : required(engineId);
        var target = target(registration == null ? null : registration.getTargetKey());
        if (registration != null) requireBinding(registration, target);
        return info(target, registration);
    }

    public DispatcherInfoResponse targetInfo(String targetKey) {
        return info(target(targetKey), null);
    }

    private DispatcherInfoResponse info(DispatcherBackendRegistry.Target target, DispatcherRegistration registration) {
        var topics = registration == null ? null : new DispatcherTopics(registration.getCommandTopic(),
                registration.getRunnerEventTopic(), registration.getAdminEventTopic(), registration.getRunnerControlTopic());
        return new DispatcherInfoResponse(identity.instanceId().toString(), target.backend().type(), "0.1.0-SNAPSHOT",
                capabilities(target), List.of(dependency("backend", backends.readiness(target)),
                dependency("artifact-storage", artifacts.readiness()), dependency("kafka", listeners.readiness(topics)),
                new DispatcherDependency("kafka-listeners", registration != null && listeners.listenersRunning(registration.getEngineId()) ? "UP" : "DOWN", null)));
    }

    private static DispatcherDependency dependency(String name, BackendReadiness readiness) {
        return new DispatcherDependency(name, readiness.ready() ? "UP" : "DOWN", String.join("; ", readiness.issues()));
    }

    private <T> T lifecycle(UUID engineId, java.util.function.Supplier<T> operation) {
        var lock = lifecycleLocks.computeIfAbsent(engineId, ignored -> new java.util.concurrent.locks.ReentrantLock());
        if (!lock.tryLock()) throw conflict("此引擎正在执行注册管理操作，请稍后重试");
        try { return operation.get(); }
        finally { lock.unlock(); }
    }

    private static DispatcherCapabilities capabilities(DispatcherBackendRegistry.Target target) {
        var streaming = target.configuration().streaming();
        boolean supported = streaming != null && streaming.configured()
                && (target.backend().type() == cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType.LOCAL_DOCKER
                    || !streaming.checkpointBaseUri().startsWith("file:"));
        return new DispatcherCapabilities(true, true, true, supported, supported);
    }

    public DispatcherRegistrationResponse current() { return current(null); }
    public DispatcherRegistrationResponse current(UUID engineId) {
        return DispatcherRegistrationResponse.from(engineId == null ? legacyRegistration(true) : required(engineId));
    }

    public DispatcherRegistrationResponse activate(DispatcherRegistrationRequest request) {
        return lifecycle(request.engineId(), () -> activateLocked(request));
    }

    private DispatcherRegistrationResponse activateLocked(DispatcherRegistrationRequest request) {
        if (messaging.shared() && !messaging.topics().equals(request.topics())) {
            throw conflict("消息通道与 Dispatcher 实例配置不一致，请重新发现；旧通道须在维护窗口迁移");
        }
        var target = target(request.targetKey());
        if (!Objects.equals(request.resourcePolicy(), target.configuration().resourcePolicy())) {
            throw conflict("资源策略与 Dispatcher 部署配置不一致，请重新发现目标后注册");
        }
        UUID instanceId = identity.instanceId();
        if (!backends.legacy() && (!instanceId.toString().equals(request.dispatcherInstanceId())
                || request.expectedBackendType() != target.backend().type()
                || !target.fingerprint().equals(request.targetFingerprint()))) {
            throw conflict("目标发现结果已变化，请重新连接 Dispatcher 并选择目标");
        }
        if (!backends.readiness(target).ready()) throw unavailable("执行目标尚未就绪");
        if (!artifacts.readiness().ready()) throw unavailable("任务制品存储尚未就绪");
        var messagingReadiness = listeners.readiness(request.topics());
        if (!messagingReadiness.ready()) throw unavailable("Kafka 或执行 Topic 尚未就绪");
        var saved = Objects.requireNonNull(transaction.execute(status -> {
            identity.lockRegistrations();
            var existing = repository.findByEngineIdForUpdate(request.engineId()).orElse(null);
            var topics = request.topics();
            var policy = request.admissionPolicy();
            for (var other : repository.findAll()) {
                if (other.getEngineId().equals(request.engineId())) continue;
                String otherKey = other.getTargetKey() == null && backends.legacy() ? "default" : other.getTargetKey();
                if (backends.legacy() && other.getState() == DispatcherRegistrationState.INACTIVE && !hasWork(other.getEngineId())) {
                    repository.delete(other);
                    repository.flush();
                    continue;
                }
                if (Objects.equals(otherKey, target.key()) && other.getState() != DispatcherRegistrationState.INACTIVE) {
                    throw conflict("执行目标已绑定其他计算引擎");
                }
                if (messaging.shared()) continue;
                var exclusive = new HashSet<>(List.of(other.getCommandTopic(), other.getRunnerEventTopic(), other.getRunnerControlTopic()));
                if (exclusive.contains(topics.commandTopic()) || exclusive.contains(topics.runnerEventTopic())
                        || exclusive.contains(topics.runnerControlTopic()) || exclusive.contains(topics.adminEventTopic())
                        || List.of(topics.commandTopic(), topics.runnerEventTopic(), topics.runnerControlTopic()).contains(other.getAdminEventTopic())) {
                    throw conflict("执行 Topic 与其他引擎冲突");
                }
            }
            if (existing != null) {
                requireBinding(existing, target);
                if (existing.getState() == DispatcherRegistrationState.ACTIVE) {
                    if (existing.sameConfiguration(topics.commandTopic(), topics.runnerEventTopic(), topics.adminEventTopic(), topics.runnerControlTopic(),
                            policy.maxQueuedExecutions(), policy.maxConcurrentSubmissions(), policy.maxInFlightApplications(), request.resourcePolicy())) return existing;
                    throw conflict("活动注册不能直接替换配置，请先 Drain 并反注册");
                }
                if (existing.getState() != DispatcherRegistrationState.INACTIVE && existing.getState() != DispatcherRegistrationState.ERROR) {
                    throw conflict("当前引擎注册不能重新激活");
                }
                if (hasWork(existing.getEngineId())) throw conflict("原执行与清理责任尚未完成");
                existing.reactivate(target.backend().type(), topics.commandTopic(), topics.runnerEventTopic(), topics.adminEventTopic(), topics.runnerControlTopic(),
                        policy.maxQueuedExecutions(), policy.maxConcurrentSubmissions(), policy.maxInFlightApplications(), request.resourcePolicy());
            } else {
                existing = DispatcherRegistration.activate(request.engineId(), instanceId, target.backend().type(),
                        topics.commandTopic(), topics.runnerEventTopic(), topics.adminEventTopic(), topics.runnerControlTopic(),
                        policy.maxQueuedExecutions(), policy.maxConcurrentSubmissions(), policy.maxInFlightApplications(), request.resourcePolicy());
            }
            existing.bindTarget(target.key(), target.fingerprint());
            return repository.saveAndFlush(existing);
        }));
        try {
            if (!listeners.listenersRunning(saved.getEngineId())) listeners.start(saved);
            if (!listeners.listenersRunning(saved.getEngineId())) throw unavailable("Kafka Listener 启动失败");
        } catch (RuntimeException exception) {
            listeners.stop(saved.getEngineId());
            transaction.executeWithoutResult(status -> { var row = locked(saved.getEngineId()); row.deactivate(); repository.save(row); });
            throw unavailable("Kafka Listener 启动失败");
        }
        return DispatcherRegistrationResponse.from(saved);
    }

    public DispatcherRegistrationResponse drain() { return drain(null); }
    public DispatcherRegistrationResponse drain(UUID engineId) {
        UUID selected = engineId == null ? legacyRegistration(true).getEngineId() : engineId;
        return lifecycle(selected, () -> drainLocked(selected));
    }

    private DispatcherRegistrationResponse drainLocked(UUID selected) {
        return DispatcherRegistrationResponse.from(Objects.requireNonNull(transaction.execute(status -> {
            var row = locked(selected);
            row.drain();
            return repository.saveAndFlush(row);
        })));
    }

    public DispatcherRegistrationResponse resume(UUID engineId) {
        UUID selected = engineId == null ? legacyRegistration(true).getEngineId() : engineId;
        return lifecycle(selected, () -> {
            var current = required(selected);
            if (current.getState() != DispatcherRegistrationState.DRAINING) throw conflict("只有暂停调度的引擎可以恢复");
            var target = target(current.getTargetKey());
            requireBinding(current, target);
            if (!backends.readiness(target).ready() || !artifacts.readiness().ready()
                    || !listeners.listenersRunning(selected)) throw unavailable("执行目标、制品存储或消息监听尚未就绪");
            return DispatcherRegistrationResponse.from(Objects.requireNonNull(transaction.execute(status -> {
                var row = locked(selected);
                if (row.getState() != DispatcherRegistrationState.DRAINING) throw conflict("引擎状态已变化，请刷新后重试");
                row.resume();
                return repository.saveAndFlush(row);
            })));
        });
    }

    public DispatcherRegistrationResponse deactivate(boolean force) { return deactivate(null, force); }
    public DispatcherRegistrationResponse deactivate(UUID engineId, boolean force) {
        UUID selected = engineId == null ? legacyRegistration(true).getEngineId() : engineId;
        return lifecycle(selected, () -> deactivateLocked(selected, force));
    }

    private DispatcherRegistrationResponse deactivateLocked(UUID selected, boolean force) {
        if (force) transaction.executeWithoutResult(status -> {
            var row = locked(selected);
            row.beginForcedDeactivation();
            var active = executions.findAllByEngineIdAndStateIn(selected, ACTIVE_STATES);
            active.forEach(execution -> {
                boolean queued = execution.getState() == DispatcherExecutionState.QUEUED;
                execution.requestCancel();
                if (queued) events.enqueue(execution,
                        cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType.EXECUTION_CANCELLED, null, null);
            });
            executions.saveAll(active);
            repository.save(row);
        });
        Instant until = Instant.now().plus(properties.deactivationTimeout());
        do {
            var saved = transaction.execute(status -> {
                var row = locked(selected);
                if (hasWork(selected)) return null;
                row.deactivate();
                return repository.saveAndFlush(row);
            });
            if (saved != null) {
                listeners.stop(selected);
                return DispatcherRegistrationResponse.from(saved);
            }
            if (!force) throw conflict("该引擎仍有排队、活动执行或待清理资源");
            LockSupport.parkNanos(java.time.Duration.ofMillis(200).toNanos());
            if (Thread.currentThread().isInterrupted()) throw unavailable("强制反注册等待被中断");
        } while (Instant.now().isBefore(until));
        throw conflict("该引擎仍在取消和清理中，保持 DRAINING，请稍后重试");
    }

    private boolean hasWork(UUID engineId) {
        return executions.existsByEngineIdAndStateIn(engineId, ACTIVE_STATES) || executions.countPendingCleanup(engineId) > 0;
    }
    private DispatcherRegistration legacyRegistration(boolean required) {
        if (!backends.legacy()) throw conflict("该 Dispatcher 使用多目标控制面，请升级 Admin 并指定 engineId");
        var rows = repository.findAll();
        if (rows.size() > 1) throw conflict("存在多个引擎注册，必须指定 engineId");
        if (rows.isEmpty() && required) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispatcher 尚未注册");
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private DispatcherRegistration required(UUID id) {
        return repository.findByEngineId(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "计算引擎尚未注册"));
    }
    private DispatcherRegistration locked(UUID id) {
        return repository.findByEngineIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "计算引擎尚未注册"));
    }
    private DispatcherBackendRegistry.Target target(String key) {
        try { return backends.require(key); }
        catch (IllegalArgumentException exception) { throw conflict(exception.getMessage()); }
    }
    private void requireBinding(DispatcherRegistration row, DispatcherBackendRegistry.Target target) {
        if (row.getBackendType() != target.backend().type()
                || (row.getTargetKey() != null && !row.getTargetKey().equals(target.key()))
                || (row.getTargetFingerprint() != null && !row.getTargetFingerprint().equals(target.fingerprint()))
                || (!backends.legacy() && row.getTargetKey() == null)) throw conflict("引擎目标身份不匹配或旧记录尚未迁移");
    }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static ResponseStatusException unavailable(String message) { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message); }
}
