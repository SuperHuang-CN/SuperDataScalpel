package cn.superhuang.data.scalpel.business.compute.service;

import cn.superhuang.data.scalpel.business.compute.client.ComputeEngineDispatcherClient;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherAdmissionPolicy;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherInfoResponse;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherRegistrationRequest;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherRegistrationResponse;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherRegistrationState;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherTopics;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngine;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineRegistrationState;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.compute.web.request.CreateComputeEngineRequest;
import cn.superhuang.data.scalpel.business.compute.web.request.DetachComputeEngineRequest;
import cn.superhuang.data.scalpel.business.compute.web.request.UpdateComputeEngineRequest;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeEngineResponse;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeEngineTestResponse;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.execution.domain.TaskExecutionOutboxState;
import cn.superhuang.data.scalpel.business.task.execution.repository.TaskExecutionOutboxRepository;
import cn.superhuang.data.scalpel.business.task.execution.service.ExecutionKafkaProperties;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ComputeEngineManagementService {

    private static final List<TaskRunStatus> ACTIVE_RUN_STATES = List.of(
            TaskRunStatus.QUEUED,
            TaskRunStatus.RUNNING,
            TaskRunStatus.CANCEL_REQUESTED,
            TaskRunStatus.STOP_REQUESTED
    );
    private static final List<TaskExecutionOutboxState> UNPUBLISHED_OUTBOX_STATES = List.of(
            TaskExecutionOutboxState.PENDING,
            TaskExecutionOutboxState.PUBLISHING,
            TaskExecutionOutboxState.FAILED
    );

    private final ComputeEngineRepository repository;
    private final DataTaskRepository taskRepository;
    private final SparkJarTaskDefinitionRepository sparkJarDefinitionRepository;
    private final TaskRunRepository taskRunRepository;
    private final TaskExecutionOutboxRepository executionOutboxRepository;
    private final SearchEngine searchEngine;
    private final ComputeEngineCredentialCipher credentialCipher;
    private final ComputeEngineDispatcherClient client;
    private final ExecutionKafkaProperties executionKafkaProperties;
    private final SparkExecutionResourceConfigurationService resourceConfigurationService;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentHashMap<UUID, ReentrantLock> reconfigurationLocks = new ConcurrentHashMap<>();

    public ComputeEngineManagementService(
            ComputeEngineRepository repository,
            DataTaskRepository taskRepository,
            SparkJarTaskDefinitionRepository sparkJarDefinitionRepository,
            TaskRunRepository taskRunRepository,
            TaskExecutionOutboxRepository executionOutboxRepository,
            SearchEngine searchEngine,
            ComputeEngineCredentialCipher credentialCipher,
            ComputeEngineDispatcherClient client,
            ExecutionKafkaProperties executionKafkaProperties,
            SparkExecutionResourceConfigurationService resourceConfigurationService,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.sparkJarDefinitionRepository = sparkJarDefinitionRepository;
        this.taskRunRepository = taskRunRepository;
        this.executionOutboxRepository = executionOutboxRepository;
        this.searchEngine = searchEngine;
        this.credentialCipher = credentialCipher;
        this.client = client;
        this.executionKafkaProperties = executionKafkaProperties;
        this.resourceConfigurationService = resourceConfigurationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public PageResponse<ComputeEngineResponse> search(SearchRequest request) {
        Page<ComputeEngine> result = searchEngine.search(request, ComputeEngine.class, repository);
        return new PageResponse<>(
                result.getContent().stream().map(this::response).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public ComputeEngineResponse get(UUID id) {
        return response(requireEngine(id));
    }

    @Transactional
    public ComputeEngineResponse create(CreateComputeEngineRequest request) {
        requireUniqueName(request.name(), null);
        requireUniqueTopics(request.commandTopic(), request.runnerEventTopic(), request.adminEventTopic(), null);
        requireListenedAdminEventTopic(request.adminEventTopic());
        SparkExecutionResourcePolicy resourcePolicy = requestedPolicy(request.resourcePolicy(), request.expectedBackendType());
        ComputeEngine engine = ComputeEngine.create(
                request.name(), request.description(), request.dispatcherBaseUrl(),
                credentialCipher.encrypt(request.accessToken()), request.expectedBackendType(),
                request.commandTopic(), request.runnerEventTopic(), request.adminEventTopic(),
                request.maxQueuedExecutions(), request.maxConcurrentSubmissions(), request.maxInFlightApplications(),
                resourceConfigurationService.write(resourcePolicy)
        );
        return response(repository.saveAndFlush(engine));
    }

    @Transactional
    public ComputeEngineResponse update(UUID id, UpdateComputeEngineRequest request) {
        ComputeEngine engine = requireEngine(id);
        requireReadOnlyResourcePolicy(engine, request.resourcePolicy());
        requireEditable(engine);
        requireUniqueName(request.name(), id);
        requireUniqueTopics(request.commandTopic(), request.runnerEventTopic(), request.adminEventTopic(), id);
        requireListenedAdminEventTopic(request.adminEventTopic());
        String ciphertext = request.accessToken() == null || request.accessToken().isBlank()
                ? engine.getAccessTokenCiphertext() : credentialCipher.encrypt(request.accessToken());
        SparkExecutionResourcePolicy resourcePolicy = request.resourcePolicy() == null
                ? resourceConfigurationService.policy(engine.getResourcePolicyJson(), engine.getExpectedBackendType())
                : request.resourcePolicy();
        assertJarTasksWithinMaximums(id, resourcePolicy.maximums());
        engine.update(
                request.name(), request.description(), request.dispatcherBaseUrl(), ciphertext,
                request.expectedBackendType(), request.commandTopic(), request.runnerEventTopic(),
                request.adminEventTopic(), request.maxQueuedExecutions(),
                request.maxConcurrentSubmissions(), request.maxInFlightApplications(),
                resourceConfigurationService.write(resourcePolicy)
        );
        return response(repository.saveAndFlush(engine));
    }

    public ComputeEngineResponse reconfigure(UUID id, UpdateComputeEngineRequest request) {
        ReentrantLock lock = reconfigurationLocks.computeIfAbsent(id, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "计算引擎配置正在变更，请稍后重试");
        }
        try {
            return reconfigureLocked(id, request);
        } finally {
            lock.unlock();
        }
    }

    private ComputeEngineResponse reconfigureLocked(UUID id, UpdateComputeEngineRequest request) {
        ReconfigurationPlan plan = Objects.requireNonNull(transactionTemplate.execute(status -> prepareReconfiguration(id, request)));
        if (!plan.changed()) {
            return response(requireEngine(id));
        }

        preflightCandidate(plan.candidate());
        EngineSnapshot current = plan.current();
        if (current.registrationState() == ComputeEngineRegistrationState.ACTIVE) {
            current = drainForReconfiguration(current);
        }
        deactivateForReconfiguration(current);
        applyReconfiguration(current, plan.candidate());
        return register(id);
    }

    public ComputeEngineTestResponse test(UUID id) {
        EngineSnapshot snapshot = snapshot(id);
        try {
            DispatcherInfoResponse info = requireInfo(snapshot, remoteInfo(snapshot));
            updateIfCurrent(snapshot, engine -> engine.markHealthy(
                    info.dispatcherInstanceId(), info.backendType()
            ));
            return new ComputeEngineTestResponse(
                    info.dispatcherInstanceId(), info.backendType(), info.version(),
                    info.capabilities(), info.dependencies()
            );
        } catch (RuntimeException exception) {
            updateFailure(snapshot, exception, false);
            throw upstream("测试计算引擎失败", exception);
        }
    }

    public ComputeEngineResponse register(UUID id) {
        refreshDeploymentPolicy(id);
        EngineSnapshot snapshot = Objects.requireNonNull(transactionTemplate.execute(status -> {
            ComputeEngine engine = requireEngine(id);
            requireListenedAdminEventTopic(engine.getAdminEventTopic());
            if (engine.getRegistrationState() == ComputeEngineRegistrationState.ACTIVE
                    || engine.getRegistrationState() == ComputeEngineRegistrationState.DRAINING
                    || engine.getRegistrationState() == ComputeEngineRegistrationState.REGISTERING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "计算引擎当前状态不能注册");
            }
            engine.beginRegistration();
            repository.saveAndFlush(engine);
            return snapshot(engine);
        }));
        try {
            String token = credentialCipher.decrypt(snapshot.tokenCiphertext());
            DispatcherInfoResponse info = requireInfo(snapshot, remoteInfo(snapshot));
            DispatcherRegistrationResponse registration = client.activate(
                    snapshot.baseUrl(), token, new DispatcherRegistrationRequest(
                            snapshot.id(),
                            new DispatcherTopics(snapshot.commandTopic(), snapshot.runnerEventTopic(), snapshot.adminEventTopic()),
                            new DispatcherAdmissionPolicy(
                                    snapshot.maxQueuedExecutions(), snapshot.maxConcurrentSubmissions(),
                                    snapshot.maxInFlightApplications()
                            ),
                            resourceConfigurationService.policy(snapshot.resourcePolicyJson(), snapshot.expectedBackendType()),
                            snapshot.targetKey(), snapshot.targetDispatcherInstanceId(),
                            SparkExecutionResourceConfigurationService.toExecutionBackend(snapshot.expectedBackendType()),
                            snapshot.targetFingerprint()
                    )
            );
            requireRegistration(
                    snapshot, registration, DispatcherRegistrationState.ACTIVE, info.dispatcherInstanceId());
            return updateIfCurrent(snapshot, engine -> engine.activate(
                    info.dispatcherInstanceId(), info.backendType()
            ));
        } catch (RuntimeException exception) {
            updateFailure(snapshot, exception, true);
            throw upstream("注册计算引擎失败", exception);
        }
    }

    public ComputeEngineResponse drain(UUID id) {
        EngineSnapshot snapshot = snapshot(id);
        if (snapshot.registrationState() != ComputeEngineRegistrationState.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有 ACTIVE 计算引擎可以 Drain");
        }
        try {
            String token = credentialCipher.decrypt(snapshot.tokenCiphertext());
            requireOwnedRemoteRegistration(snapshot, remoteRegistration(snapshot, token));
            DispatcherRegistrationResponse registration = remoteDrain(snapshot, token);
            requireRegistration(snapshot, registration, DispatcherRegistrationState.DRAINING);
            return updateIfCurrent(snapshot, ComputeEngine::markDraining);
        } catch (RuntimeException exception) {
            updateFailure(snapshot, exception, false);
            throw upstream("计算引擎 Drain 失败", exception);
        }
    }

    public ComputeEngineResponse resume(UUID id) {
        EngineSnapshot snapshot = snapshot(id);
        if (snapshot.registrationState() != ComputeEngineRegistrationState.DRAINING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有暂停调度的计算引擎可以恢复");
        }
        try {
            String token = credentialCipher.decrypt(snapshot.tokenCiphertext());
            requireOwnedRemoteRegistration(snapshot, remoteRegistration(snapshot, token));
            var registration = client.resume(snapshot.baseUrl(), token, snapshot.targetKey() == null ? null : snapshot.id());
            requireRegistration(snapshot, registration, DispatcherRegistrationState.ACTIVE);
            return updateIfCurrent(snapshot, engine -> engine.activate(registration.dispatcherInstanceId(), registration.backendType()));
        } catch (RuntimeException exception) {
            updateFailure(snapshot, exception, false);
            throw upstream("恢复任务调度失败", exception);
        }
    }

    public ComputeEngineResponse deactivate(UUID id, boolean force) {
        EngineSnapshot snapshot = snapshot(id);
        if (snapshot.registrationState() != ComputeEngineRegistrationState.ACTIVE
                && snapshot.registrationState() != ComputeEngineRegistrationState.DRAINING
                && snapshot.registrationState() != ComputeEngineRegistrationState.ERROR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前计算引擎未处于可反注册状态");
        }
        try {
            String token = credentialCipher.decrypt(snapshot.tokenCiphertext());
            requireOwnedRemoteRegistration(snapshot, remoteRegistration(snapshot, token));
            DispatcherRegistrationResponse registration = remoteDeactivate(snapshot, token, force);
            requireRegistration(snapshot, registration, DispatcherRegistrationState.INACTIVE);
            return updateIfCurrent(snapshot, ComputeEngine::markInactive);
        } catch (RuntimeException exception) {
            if (isConflict(exception)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, force
                        ? "暂不能停用引擎：请检查注册状态及任务取消、资源清理进度，确认完成后重试"
                        : "暂不能停用引擎：请检查注册状态，并确认没有排队、运行中任务或待清理资源", exception);
            }
            updateFailure(snapshot, exception, false);
            throw upstream("计算引擎反注册失败", exception);
        }
    }

    public ComputeEngineResponse detach(UUID id, DetachComputeEngineRequest request) {
        EngineSnapshot snapshot = snapshot(id);
        if (snapshot.registrationState() != ComputeEngineRegistrationState.ACTIVE
                && snapshot.registrationState() != ComputeEngineRegistrationState.DRAINING
                && snapshot.registrationState() != ComputeEngineRegistrationState.ERROR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前计算引擎未处于可离线解除绑定状态");
        }
        if (!snapshot.name().equals(request.confirmationName().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "确认名称与计算引擎名称不一致");
        }
        requireDispatcherTransportUnavailable(snapshot);

        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            ComputeEngine engine = repository.findByIdForUpdate(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "计算引擎不存在"));
            requireUnchangedTarget(engine, snapshot);
            if (engine.getRegistrationState() != ComputeEngineRegistrationState.ACTIVE
                    && engine.getRegistrationState() != ComputeEngineRegistrationState.DRAINING
                    && engine.getRegistrationState() != ComputeEngineRegistrationState.ERROR) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "计算引擎状态已变化，请重新操作");
            }
            if (taskRunRepository.existsByComputeEngineIdAndStatusIn(id, ACTIVE_RUN_STATES)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "计算引擎仍有关联的排队、运行或待取消任务，不能离线解除绑定"
                );
            }
            if (executionOutboxRepository.existsByEngineIdAndStateIn(id, UNPUBLISHED_OUTBOX_STATES)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "计算引擎仍有尚未发布完成的执行消息，不能离线解除绑定"
                );
            }
            engine.detach(request.reason());
            return response(repository.saveAndFlush(engine));
        }));
    }

    @Transactional
    public void delete(UUID id) {
        ComputeEngine engine = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "计算引擎不存在"));
        if (engine.getTargetKey() != null && engine.getRegistrationState() == ComputeEngineRegistrationState.ERROR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "异常引擎的远端注册结果可能不确定，请先安全反注册或完成离线解绑");
        }
        if (engine.getRegistrationState() == ComputeEngineRegistrationState.ACTIVE
                || engine.getRegistrationState() == ComputeEngineRegistrationState.DRAINING
                || engine.getRegistrationState() == ComputeEngineRegistrationState.REGISTERING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "活动或正在注册的计算引擎不能删除");
        }
        if (taskRepository.existsByComputeEngineId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "计算引擎已被任务使用，不能删除");
        }
        repository.delete(engine);
    }

    private ComputeEngineResponse updateIfCurrent(EngineSnapshot snapshot, java.util.function.Consumer<ComputeEngine> action) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            ComputeEngine engine = requireEngine(snapshot.id());
            requireUnchangedTarget(engine, snapshot);
            action.accept(engine);
            return response(repository.saveAndFlush(engine));
        }));
    }

    private ReconfigurationPlan prepareReconfiguration(UUID id, UpdateComputeEngineRequest request) {
        ComputeEngine engine = requireEngine(id);
        requireReadOnlyResourcePolicy(engine, request.resourcePolicy());
        if (engine.getRegistrationState() != ComputeEngineRegistrationState.ACTIVE
                && engine.getRegistrationState() != ComputeEngineRegistrationState.DRAINING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有 ACTIVE 或 DRAINING 计算引擎可以自动重新配置");
        }
        requireUniqueName(request.name(), id);
        requireUniqueTopics(request.commandTopic(), request.runnerEventTopic(), request.adminEventTopic(), id);
        requireListenedAdminEventTopic(request.adminEventTopic());
        String ciphertext = request.accessToken() == null || request.accessToken().isBlank()
                ? engine.getAccessTokenCiphertext() : credentialCipher.encrypt(request.accessToken());
        SparkExecutionResourcePolicy resourcePolicy = request.resourcePolicy() == null
                ? resourceConfigurationService.policy(engine.getResourcePolicyJson(), engine.getExpectedBackendType())
                : request.resourcePolicy();
        assertJarTasksWithinMaximums(id, resourcePolicy.maximums());
        ComputeEngine candidate = ComputeEngine.create(
                request.name(), request.description(), request.dispatcherBaseUrl(), ciphertext,
                request.expectedBackendType(), request.commandTopic(), request.runnerEventTopic(),
                request.adminEventTopic(), request.maxQueuedExecutions(),
                request.maxConcurrentSubmissions(), request.maxInFlightApplications(),
                resourceConfigurationService.write(resourcePolicy)
        );
        if (engine.getTargetKey() != null) {
            if (candidate.getExpectedBackendType() != engine.getExpectedBackendType()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "已绑定执行目标的后端类型不可修改");
            }
            candidate.bindTarget(engine.getTargetDispatcherInstanceId(), engine.getTargetKey(), engine.getTargetFingerprint());
        }
        if (taskRunRepository.existsByComputeEngineIdAndStatusIn(id, ACTIVE_RUN_STATES)
                || executionOutboxRepository.existsByEngineIdAndStateIn(id, UNPUBLISHED_OUTBOX_STATES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仍有活动运行或未发布执行消息，不能删除计算引擎");
        }
        return new ReconfigurationPlan(snapshot(engine), candidate, !engine.sameConfigurationAs(candidate));
    }

    private void preflightCandidate(ComputeEngine candidate) {
        try {
            DispatcherInfoResponse info = remoteInfo(snapshot(candidate));
            if (info == null || info.dispatcherInstanceId() == null || info.dispatcherInstanceId().isBlank()
                    || info.backendType() != candidate.getExpectedBackendType()) {
                throw new IllegalStateException("候选 Dispatcher 信息缺失或后端类型不一致");
            }
        } catch (RuntimeException exception) {
            throw upstream("新计算引擎配置预检失败", exception);
        }
    }

    private EngineSnapshot drainForReconfiguration(EngineSnapshot snapshot) {
        try {
            String token = credentialCipher.decrypt(snapshot.tokenCiphertext());
            requireOwnedRemoteRegistration(snapshot, remoteRegistration(snapshot, token));
            DispatcherRegistrationResponse registration = remoteDrain(snapshot, token);
            requireRegistration(snapshot, registration, DispatcherRegistrationState.DRAINING);
            updateIfCurrent(snapshot, ComputeEngine::markDraining);
            return snapshot(snapshot.id());
        } catch (RuntimeException exception) {
            updateFailure(snapshot, exception, false);
            throw upstream("计算引擎 Drain 失败", exception);
        }
    }

    private void deactivateForReconfiguration(EngineSnapshot snapshot) {
        try {
            String token = credentialCipher.decrypt(snapshot.tokenCiphertext());
            requireOwnedRemoteRegistration(snapshot, remoteRegistration(snapshot, token));
            DispatcherRegistrationResponse registration = remoteDeactivate(snapshot, token, false);
            requireRegistration(snapshot, registration, DispatcherRegistrationState.INACTIVE);
            updateIfCurrent(snapshot, ComputeEngine::markInactive);
        } catch (RuntimeException exception) {
            if (isConflict(exception)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "计算引擎仍有排队或活动任务，已进入排空状态；任务结束后请再次应用配置",
                        exception
                );
            }
            updateFailure(snapshot, exception, false);
            throw upstream("计算引擎反注册失败", exception);
        }
    }

    private void applyReconfiguration(EngineSnapshot current, ComputeEngine candidate) {
        Objects.requireNonNull(transactionTemplate.execute(status -> {
            ComputeEngine engine = requireEngine(current.id());
            requireUnchangedTarget(engine, current);
            if (engine.getRegistrationState() != ComputeEngineRegistrationState.INACTIVE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "计算引擎尚未完成反注册");
            }
            requireUniqueName(candidate.getName(), current.id());
            requireUniqueTopics(candidate.getCommandTopic(), candidate.getRunnerEventTopic(), candidate.getAdminEventTopic(), current.id());
            engine.update(
                    candidate.getName(), candidate.getDescription(), candidate.getDispatcherBaseUrl(),
                    candidate.getAccessTokenCiphertext(), candidate.getExpectedBackendType(),
                    candidate.getCommandTopic(), candidate.getRunnerEventTopic(), candidate.getAdminEventTopic(),
                    candidate.getMaxQueuedExecutions(), candidate.getMaxConcurrentSubmissions(),
                    candidate.getMaxInFlightApplications(), candidate.getResourcePolicyJson()
            );
            return repository.saveAndFlush(engine);
        }));
    }

    private void updateFailure(EngineSnapshot snapshot, RuntimeException exception, boolean registrationFailure) {
        transactionTemplate.executeWithoutResult(status -> repository.findById(snapshot.id()).ifPresent(engine -> {
            if (!sameTarget(engine, snapshot)) {
                return;
            }
            String message = safeMessage(exception);
            if (registrationFailure) engine.markRegistrationFailure(message);
            else engine.markHealthFailure(message);
            repository.save(engine);
        }));
    }

    private void requireReadOnlyResourcePolicy(ComputeEngine engine, SparkExecutionResourcePolicy requested) {
        if (engine.getTargetKey() != null && requested != null && !requested.equals(
                resourceConfigurationService.policy(engine.getResourcePolicyJson(), engine.getExpectedBackendType()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "资源默认值与上限由 Dispatcher 部署配置维护，Admin 不能修改");
        }
    }

    private void refreshDeploymentPolicy(UUID id) {
        EngineSnapshot before = snapshot(id);
        if (before.targetKey() == null) return;
        if (before.registrationState() == ComputeEngineRegistrationState.ACTIVE
                || before.registrationState() == ComputeEngineRegistrationState.DRAINING
                || before.registrationState() == ComputeEngineRegistrationState.REGISTERING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先停止接收任务并停用注册，再同步部署资源策略");
        }
        var directory = client.targets(before.baseUrl(), credentialCipher.decrypt(before.tokenCiphertext()));
        if (directory == null || !Objects.equals(before.targetDispatcherInstanceId(), directory.dispatcherInstanceId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dispatcher 实例身份已变化");
        }
        var target = directory.targets().stream().filter(value -> before.targetKey().equals(value.targetKey()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "执行目标已移除"));
        if (!Objects.equals(before.targetFingerprint(), target.targetFingerprint())
                || !before.expectedBackendType().name().equals(target.backendType().name())
                || target.resourcePolicy() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标已变化或未提供资源策略，请重新发现");
        }
        updateIfCurrent(before, engine -> engine.synchronizeResourcePolicy(resourceConfigurationService.write(target.resourcePolicy())));
    }

    private DispatcherInfoResponse remoteInfo(EngineSnapshot snapshot) {
        String token = credentialCipher.decrypt(snapshot.tokenCiphertext());
        if (snapshot.targetKey() == null) return client.info(snapshot.baseUrl(), token);
        var directory = client.targets(snapshot.baseUrl(), token);
        if (directory == null || !Objects.equals(snapshot.targetDispatcherInstanceId(), directory.dispatcherInstanceId())) {
            throw new IllegalStateException("Dispatcher 实例身份与已绑定目标不一致");
        }
        var target = directory.targets().stream().filter(t -> snapshot.targetKey().equals(t.targetKey()))
                .findFirst().orElseThrow(() -> new IllegalStateException("执行目标已禁用或移除"));
        if (!Objects.equals(snapshot.targetFingerprint(), target.targetFingerprint())
                || !snapshot.expectedBackendType().name().equals(target.backendType().name())) {
            throw new IllegalStateException("执行目标的物理环境已变化，请检查部署配置");
        }
        return client.targetInfo(snapshot.baseUrl(), token, snapshot.targetKey());
    }

    private DispatcherRegistrationResponse remoteRegistration(EngineSnapshot snapshot, String token) {
        return snapshot.targetKey() == null ? client.registration(snapshot.baseUrl(), token)
                : client.registration(snapshot.baseUrl(), token, snapshot.id());
    }

    private DispatcherRegistrationResponse remoteDrain(EngineSnapshot snapshot, String token) {
        return snapshot.targetKey() == null ? client.drain(snapshot.baseUrl(), token)
                : client.drain(snapshot.baseUrl(), token, snapshot.id());
    }

    private DispatcherRegistrationResponse remoteDeactivate(EngineSnapshot snapshot, String token, boolean force) {
        return snapshot.targetKey() == null ? client.deactivate(snapshot.baseUrl(), token, force)
                : client.deactivate(snapshot.baseUrl(), token, snapshot.id(), force);
    }

    private DispatcherInfoResponse requireInfo(EngineSnapshot snapshot, DispatcherInfoResponse info) {
        if (info == null || info.dispatcherInstanceId() == null || info.dispatcherInstanceId().isBlank()
                || info.backendType() == null) {
            throw new IllegalStateException("Dispatcher info 响应缺失");
        }
        if (info.backendType() != snapshot.expectedBackendType()) {
            throw new IllegalStateException("Dispatcher 实际后端与计算引擎配置不一致");
        }
        if (snapshot.targetDispatcherInstanceId() != null
                && !snapshot.targetDispatcherInstanceId().equals(info.dispatcherInstanceId())) {
            throw new IllegalStateException("Dispatcher 不是已绑定目标所属实例");
        }
        if (snapshot.dispatcherInstanceId() != null
                && !snapshot.dispatcherInstanceId().equals(info.dispatcherInstanceId())) {
            throw new IllegalStateException("Dispatcher 实例身份已变化，请确认部署后重新配置");
        }
        return info;
    }

    private void requireRegistration(
            EngineSnapshot snapshot,
            DispatcherRegistrationResponse response,
            DispatcherRegistrationState expected
    ) {
        requireRegistration(snapshot, response, expected, snapshot.targetDispatcherInstanceId() == null
                ? snapshot.dispatcherInstanceId() : snapshot.targetDispatcherInstanceId());
    }

    private void requireRegistration(
            EngineSnapshot snapshot,
            DispatcherRegistrationResponse response,
            DispatcherRegistrationState expected,
            String expectedDispatcherInstanceId
    ) {
        if (response == null || response.state() != expected || !snapshot.id().equals(response.engineId())
                || !Objects.equals(expectedDispatcherInstanceId, response.dispatcherInstanceId())
                || !sameRemoteConfiguration(snapshot, response)) {
            throw new IllegalStateException("Dispatcher 返回了不一致的注册状态");
        }
    }

    private void requireOwnedRemoteRegistration(
            EngineSnapshot snapshot,
            DispatcherRegistrationResponse response
    ) {
        if (response == null || !snapshot.id().equals(response.engineId())
                || !Objects.equals(snapshot.targetDispatcherInstanceId() == null ? snapshot.dispatcherInstanceId()
                    : snapshot.targetDispatcherInstanceId(), response.dispatcherInstanceId())
                || response.dispatcherInstanceId() == null
                || !sameRemoteConfiguration(snapshot, response)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Dispatcher 当前绑定的不是该计算引擎或实际配置不一致，不能执行管理操作"
            );
        }
    }

    private boolean sameRemoteConfiguration(
            EngineSnapshot snapshot,
            DispatcherRegistrationResponse response
    ) {
        DispatcherTopics topics = response.topics();
        DispatcherAdmissionPolicy policy = response.effectiveAdmissionPolicy();
        return response.backendType() == snapshot.expectedBackendType()
                && (snapshot.targetKey() == null || (Objects.equals(snapshot.targetKey(), response.targetKey())
                    && Objects.equals(snapshot.targetFingerprint(), response.targetFingerprint())))
                && topics != null
                && Objects.equals(topics.commandTopic(), snapshot.commandTopic())
                && Objects.equals(topics.runnerEventTopic(), snapshot.runnerEventTopic())
                && Objects.equals(topics.adminEventTopic(), snapshot.adminEventTopic())
                && Objects.equals(topics.runnerControlTopic(), snapshot.runnerEventTopic() + ".control")
                && policy != null
                && policy.maxQueuedExecutions() == snapshot.maxQueuedExecutions()
                && policy.maxConcurrentSubmissions() == snapshot.maxConcurrentSubmissions()
                && policy.maxInFlightApplications() == snapshot.maxInFlightApplications()
                && Objects.equals(response.resourcePolicy(),
                resourceConfigurationService.policy(snapshot.resourcePolicyJson(), snapshot.expectedBackendType()));
    }

    private static void requireUnchangedTarget(ComputeEngine engine, EngineSnapshot snapshot) {
        if (!sameTarget(engine, snapshot)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "计算引擎配置已变化，请重新操作");
        }
    }

    private static boolean sameTarget(ComputeEngine engine, EngineSnapshot snapshot) {
        return Objects.equals(engine.getDispatcherBaseUrl(), snapshot.baseUrl())
                && Objects.equals(engine.getTargetKey(), snapshot.targetKey())
                && Objects.equals(engine.getTargetFingerprint(), snapshot.targetFingerprint())
                && Objects.equals(engine.getTargetDispatcherInstanceId(), snapshot.targetDispatcherInstanceId())
                && Objects.equals(engine.getAccessTokenCiphertext(), snapshot.tokenCiphertext())
                && engine.getExpectedBackendType() == snapshot.expectedBackendType()
                && Objects.equals(engine.getCommandTopic(), snapshot.commandTopic())
                && Objects.equals(engine.getRunnerEventTopic(), snapshot.runnerEventTopic())
                && Objects.equals(engine.getAdminEventTopic(), snapshot.adminEventTopic())
                && engine.getMaxQueuedExecutions() == snapshot.maxQueuedExecutions()
                && engine.getMaxConcurrentSubmissions() == snapshot.maxConcurrentSubmissions()
                && engine.getMaxInFlightApplications() == snapshot.maxInFlightApplications()
                && Objects.equals(engine.getResourcePolicyJson(), snapshot.resourcePolicyJson())
                && Objects.equals(engine.getDispatcherInstanceId(), snapshot.dispatcherInstanceId());
    }

    private EngineSnapshot snapshot(UUID id) {
        return snapshot(requireEngine(id));
    }

    private EngineSnapshot snapshot(ComputeEngine engine) {
        return new EngineSnapshot(
                engine.getId(), engine.getName(), engine.getDispatcherBaseUrl(), engine.getAccessTokenCiphertext(),
                engine.getExpectedBackendType(), engine.getRegistrationState(), engine.getCommandTopic(),
                engine.getRunnerEventTopic(), engine.getAdminEventTopic(), engine.getMaxQueuedExecutions(),
                engine.getMaxConcurrentSubmissions(), engine.getMaxInFlightApplications(),
                engine.getResourcePolicyJson(), engine.getDispatcherInstanceId(),
                engine.getTargetDispatcherInstanceId(), engine.getTargetKey(), engine.getTargetFingerprint()
        );
    }

    private ComputeEngineResponse response(ComputeEngine engine) {
        return ComputeEngineResponse.from(engine,
                resourceConfigurationService.policy(engine.getResourcePolicyJson(), engine.getExpectedBackendType()));
    }

    private SparkExecutionResourcePolicy requestedPolicy(
            SparkExecutionResourcePolicy requested,
            cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType backendType
    ) {
        return requested == null
                ? SparkExecutionResourcePolicy.defaultsFor(SparkExecutionResourceConfigurationService.toExecutionBackend(backendType))
                : requested;
    }

    private void assertJarTasksWithinMaximums(UUID engineId, SparkExecutionResourceSpec maximums) {
        var backend = SparkExecutionResourceConfigurationService.toExecutionBackend(requireEngine(engineId).getExpectedBackendType());
        List<cn.superhuang.data.scalpel.business.task.domain.DataTask> tasks = taskRepository
                .findAllByComputeEngineIdAndTypeIn(engineId, List.of(TaskType.SPARK_JAR, TaskType.SPARK_STREAMING_JAR));
        List<String> affected = sparkJarDefinitionRepository.findAllByTaskIdIn(tasks.stream()
                        .map(cn.superhuang.data.scalpel.business.task.domain.DataTask::getId).toList()).stream()
                .filter(definition -> {
                    SparkExecutionResourceSpec resources = resourceConfigurationService.resources(
                            definition.getExecutionResourcesJson());
                    return resources != null && resources.exceeds(maximums, backend);
                })
                .map(definition -> tasks.stream().filter(task -> task.getId().equals(definition.getTaskId()))
                        .findFirst().map(cn.superhuang.data.scalpel.business.task.domain.DataTask::getName)
                        .orElse(definition.getTaskId().toString()))
                .toList();
        if (!affected.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "新的单次任务资源上限低于已绑定 Spark JAR 任务：" + String.join("、", affected));
        }
    }

    private ComputeEngine requireEngine(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "计算引擎不存在"));
    }

    private void requireUniqueName(String name, UUID currentId) {
        repository.findByNameIgnoreCase(name.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(currentId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "计算引擎名称已存在");
            }
        });
    }

    private void requireUniqueTopics(String commandTopic, String runnerEventTopic, String adminEventTopic, UUID currentId) {
        if (currentId != null) {
            var current = requireEngine(currentId);
            if (current.getTargetKey() != null) {
                if (!current.getCommandTopic().equals(commandTopic.trim()) || !current.getRunnerEventTopic().equals(runnerEventTopic.trim())
                        || !current.getAdminEventTopic().equals(adminEventTopic.trim())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "目标消息通道由 Dispatcher 部署配置维护，不允许逐引擎修改");
                }
                return;
            }
        }
        UUID excluded = currentId == null ? new UUID(0, 0) : currentId;
        if (repository.existsByCommandTopicAndIdNot(commandTopic.trim(), excluded)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "命令 Topic 已被其他计算引擎使用");
        }
        if (repository.existsByRunnerEventTopicAndIdNot(runnerEventTopic.trim(), excluded)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Runner 事件 Topic 已被其他计算引擎使用");
        }
    }

    private void requireListenedAdminEventTopic(String topic) {
        if (!executionKafkaProperties.listensTo(topic)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Admin 事件 Topic 未包含在当前 Admin 的监听配置中：" + topic
            );
        }
    }

    private void requireDispatcherTransportUnavailable(EngineSnapshot snapshot) {
        try {
            remoteInfo(snapshot);
        } catch (ResourceAccessException expectedTransportFailure) {
            return;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Dispatcher 能够返回响应或当前失败不是连接不可达，不能执行离线解除绑定",
                    exception
            );
        }
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Dispatcher 当前仍可访问，请使用安全反注册或强制反注册"
        );
    }

    private static void requireEditable(ComputeEngine engine) {
        if (engine.getRegistrationState() == ComputeEngineRegistrationState.ACTIVE
                || engine.getRegistrationState() == ComputeEngineRegistrationState.DRAINING
                || engine.getRegistrationState() == ComputeEngineRegistrationState.REGISTERING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "活动中的计算引擎不能修改，请先 Drain 并反注册");
        }
    }

    private static ResponseStatusException upstream(String operation, RuntimeException exception) {
        if (exception instanceof ResponseStatusException response && response.getStatusCode().is4xxClientError()) {
            return response;
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, operation + "：" + safeMessage(exception), exception);
    }

    private static boolean isConflict(Throwable exception) {
        if (exception instanceof ResponseStatusException response) {
            return response.getStatusCode().value() == HttpStatus.CONFLICT.value();
        }
        if (exception instanceof RestClientResponseException response) {
            return response.getStatusCode().value() == HttpStatus.CONFLICT.value();
        }
        return exception.getCause() != null && exception.getCause() != exception && isConflict(exception.getCause());
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return "远程调用失败";
        String sanitized = message.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer ***");
        return sanitized.substring(0, Math.min(500, sanitized.length()));
    }

    private record EngineSnapshot(
            UUID id,
            String name,
            String baseUrl,
            String tokenCiphertext,
            cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType expectedBackendType,
            ComputeEngineRegistrationState registrationState,
            String commandTopic,
            String runnerEventTopic,
            String adminEventTopic,
            int maxQueuedExecutions,
            int maxConcurrentSubmissions,
            int maxInFlightApplications,
            String resourcePolicyJson,
            String dispatcherInstanceId,
            String targetDispatcherInstanceId,
            String targetKey,
            String targetFingerprint
    ) {
    }

    private record ReconfigurationPlan(
            EngineSnapshot current,
            ComputeEngine candidate,
            boolean changed
    ) {
    }
}
