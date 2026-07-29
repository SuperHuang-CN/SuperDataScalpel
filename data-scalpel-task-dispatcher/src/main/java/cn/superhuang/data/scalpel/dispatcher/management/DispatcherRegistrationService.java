package cn.superhuang.data.scalpel.dispatcher.management;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendReadiness;
import cn.superhuang.data.scalpel.dispatcher.backend.TaskExecutionBackend;
import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherArtifactService;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherProperties;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherStreamingProperties;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherExecutionState;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistrationState;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherRegistrationRepository;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.locks.LockSupport;

@Service
public class DispatcherRegistrationService {
    private static final List<DispatcherExecutionState> ACTIVE_STATES = List.of(
            DispatcherExecutionState.QUEUED, DispatcherExecutionState.SUBMITTING,
            DispatcherExecutionState.SUBMITTED, DispatcherExecutionState.RUNNING,
            DispatcherExecutionState.CANCEL_REQUESTED
    );

    private final DispatcherRegistrationRepository repository;
    private final DispatcherTaskExecutionRepository executionRepository;
    private final DispatcherIdentityService identityService;
    private final DispatcherListenerManager listenerManager;
    private final TaskExecutionBackend backend;
    private final DispatcherArtifactService artifactService;
    private final DispatcherProperties properties;
    private final DispatcherStreamingProperties streamingProperties;
    private final TransactionTemplate transactionTemplate;

    public DispatcherRegistrationService(
            DispatcherRegistrationRepository repository,
            DispatcherTaskExecutionRepository executionRepository,
            DispatcherIdentityService identityService,
            DispatcherListenerManager listenerManager,
            TaskExecutionBackend backend,
            DispatcherArtifactService artifactService,
            DispatcherProperties properties,
            DispatcherStreamingProperties streamingProperties,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.executionRepository = executionRepository;
        this.identityService = identityService;
        this.listenerManager = listenerManager;
        this.backend = backend;
        this.artifactService = artifactService;
        this.properties = properties;
        this.streamingProperties = streamingProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public DispatcherInfoResponse info() {
        UUID instanceId = identityService.instanceId();
        BackendReadiness readiness = backend.readiness();
        BackendReadiness artifacts = artifactService.readiness();
        DispatcherTopics topics = repository.findFirstByOrderByCreatedAtAsc()
                .map(value -> new DispatcherTopics(
                        value.getCommandTopic(), value.getRunnerEventTopic(), value.getAdminEventTopic(),
                        value.getRunnerControlTopic()))
                .orElse(null);
        BackendReadiness messaging = listenerManager.readiness(topics);
        List<DispatcherDependency> dependencies = List.of(
                new DispatcherDependency("backend", readiness.ready() ? "UP" : "DOWN", String.join("; ", readiness.issues())),
                new DispatcherDependency("artifact-storage", artifacts.ready() ? "UP" : "DOWN", String.join("; ", artifacts.issues())),
                new DispatcherDependency("kafka", messaging.ready() ? "UP" : "DOWN", String.join("; ", messaging.issues())),
                new DispatcherDependency("kafka-listeners", listenerManager.listenersRunning() ? "UP" : "DOWN", null)
        );
        return new DispatcherInfoResponse(
                instanceId.toString(), properties.backend(), "0.1.0-SNAPSHOT",
                new DispatcherCapabilities(
                        true, true, true,
                        streamingProperties.configured(),
                        streamingProperties.configured()),
                dependencies
        );
    }

    public DispatcherRegistrationResponse current() {
        return repository.findFirstByOrderByCreatedAtAsc().map(DispatcherRegistrationResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispatcher 尚未注册"));
    }

    public DispatcherRegistrationResponse activate(DispatcherRegistrationRequest request) {
        if (backend.type() != properties.backend() || !backend.readiness().ready()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "执行 Backend 尚未就绪");
        }
        if (!artifactService.readiness().ready()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务制品存储尚未就绪");
        }
        BackendReadiness messaging = listenerManager.readiness(request.topics());
        if (!messaging.ready()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Kafka 或执行 Topic 尚未就绪：" + String.join("; ", messaging.issues()));
        }
        UUID instanceId = identityService.instanceId();
        DispatcherRegistration registration = Objects.requireNonNull(transactionTemplate.execute(status -> {
            List<DispatcherRegistration> registrations = repository.findAllForUpdate();
            DispatcherRegistration existing = registrations.isEmpty() ? null : registrations.getFirst();
            DispatcherAdmissionPolicy policy = request.admissionPolicy();
            DispatcherTopics topics = request.topics();
            if (existing != null && existing.getState() == DispatcherRegistrationState.ACTIVE) {
                if (!existing.getEngineId().equals(request.engineId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Dispatcher 已绑定其他计算引擎");
                }
                if (existing.sameConfiguration(
                        topics.commandTopic(), topics.runnerEventTopic(), topics.adminEventTopic(),
                        topics.runnerControlTopic(),
                        policy.maxQueuedExecutions(), policy.maxConcurrentSubmissions(), policy.maxInFlightApplications()
                )) return existing;
                throw new ResponseStatusException(HttpStatus.CONFLICT, "活动注册不能直接替换配置，请先 Drain 并反注册");
            }
            if (existing != null && !existing.getEngineId().equals(request.engineId())) {
                if (existing.getState() != DispatcherRegistrationState.INACTIVE
                        && existing.getState() != DispatcherRegistrationState.ERROR) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Dispatcher 已绑定其他计算引擎");
                }
                repository.delete(existing);
                repository.flush();
                existing = null;
            }
            if (existing != null && existing.getState() != DispatcherRegistrationState.INACTIVE
                    && existing.getState() != DispatcherRegistrationState.ERROR) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前 Dispatcher 注册不能重新激活");
            }
            if (existing == null) {
                existing = DispatcherRegistration.activate(
                        request.engineId(), instanceId, properties.backend(),
                        topics.commandTopic(), topics.runnerEventTopic(), topics.adminEventTopic(),
                        topics.runnerControlTopic(),
                        policy.maxQueuedExecutions(), policy.maxConcurrentSubmissions(), policy.maxInFlightApplications()
                );
            } else {
                existing.reactivate(
                        properties.backend(),
                        topics.commandTopic(), topics.runnerEventTopic(), topics.adminEventTopic(),
                        topics.runnerControlTopic(),
                        policy.maxQueuedExecutions(), policy.maxConcurrentSubmissions(), policy.maxInFlightApplications()
                );
            }
            return repository.saveAndFlush(existing);
        }));
        listenerManager.start(registration);
        if (!listenerManager.listenersRunning()) {
            listenerManager.stopAll();
            transactionTemplate.executeWithoutResult(status -> {
                DispatcherRegistration current = locked();
                current.deactivate();
                repository.saveAndFlush(current);
            });
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Kafka Listener 启动失败");
        }
        return DispatcherRegistrationResponse.from(registration);
    }

    public DispatcherRegistrationResponse drain() {
        DispatcherRegistration registration = Objects.requireNonNull(transactionTemplate.execute(status -> {
            DispatcherRegistration current = locked();
            current.drain();
            return repository.saveAndFlush(current);
        }));
        return DispatcherRegistrationResponse.from(registration);
    }

    public DispatcherRegistrationResponse deactivate(boolean force) {
        if (force) return forceDeactivate();
        DispatcherRegistration registration = Objects.requireNonNull(transactionTemplate.execute(status -> {
            List<cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution> active =
                    executionRepository.findAllByStateIn(ACTIVE_STATES);
            if (!active.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Dispatcher 仍有排队或活动执行");
            }
            DispatcherRegistration current = locked();
            current.deactivate();
            DispatcherRegistration saved = repository.save(current);
            executionRepository.flush();
            repository.flush();
            return saved;
        }));
        listenerManager.stopAll();
        return DispatcherRegistrationResponse.from(registration);
    }

    private DispatcherRegistrationResponse forceDeactivate() {
        transactionTemplate.executeWithoutResult(status -> {
            DispatcherRegistration current = locked();
            current.beginForcedDeactivation();
            List<cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution> active =
                    executionRepository.findAllByStateIn(ACTIVE_STATES);
            active.forEach(cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution::requestCancel);
            executionRepository.saveAll(active);
            repository.save(current);
            executionRepository.flush();
            repository.flush();
        });

        Instant waitUntil = Instant.now().plus(properties.deactivationTimeout());
        while (Instant.now().isBefore(waitUntil)) {
            if (!executionRepository.existsByStateIn(ACTIVE_STATES)) {
                DispatcherRegistration registration = transactionTemplate.execute(status -> {
                    if (executionRepository.existsByStateIn(ACTIVE_STATES)) return null;
                    DispatcherRegistration current = locked();
                    current.deactivate();
                    return repository.saveAndFlush(current);
                });
                if (registration != null) {
                    listenerManager.stopAll();
                    return DispatcherRegistrationResponse.from(registration);
                }
            }
            LockSupport.parkNanos(java.time.Duration.ofMillis(200).toNanos());
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "强制反注册等待被中断");
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "活动执行仍在取消和状态收敛中，Dispatcher 保持 DRAINING，请稍后重试反注册");
    }

    private DispatcherRegistration locked() {
        List<DispatcherRegistration> values = repository.findAllForUpdate();
        if (values.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispatcher 尚未注册");
        return values.getFirst();
    }
}
