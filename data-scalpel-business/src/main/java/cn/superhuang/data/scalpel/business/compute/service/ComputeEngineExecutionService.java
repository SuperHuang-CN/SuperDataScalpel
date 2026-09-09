package cn.superhuang.data.scalpel.business.compute.service;

import cn.superhuang.data.scalpel.business.compute.client.ComputeEngineDispatcherClient;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherInfoResponse;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherExecutionResponse;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherExecutionLogResponse;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherRegistrationResponse;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherRegistrationState;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngine;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineHealthState;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineRegistrationState;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.UUID;

/** Creates and revalidates the immutable Dispatcher route used by one task run. */
@Service
public class ComputeEngineExecutionService {
    private final ComputeEngineRepository repository;
    private final ComputeEngineCredentialCipher credentialCipher;
    private final ComputeEngineDispatcherClient dispatcherClient;
    private final SparkExecutionResourceConfigurationService resourceConfigurationService;
    private final TransactionTemplate readTransaction;

    public ComputeEngineExecutionService(
            ComputeEngineRepository repository,
            ComputeEngineCredentialCipher credentialCipher,
            ComputeEngineDispatcherClient dispatcherClient,
            SparkExecutionResourceConfigurationService resourceConfigurationService,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.credentialCipher = credentialCipher;
        this.dispatcherClient = dispatcherClient;
        this.resourceConfigurationService = resourceConfigurationService;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    public ExecutionRoute requireRunnable(UUID engineId) {
        return requireRunnable(engineId, false);
    }

    public ExecutionRoute requireStreamingRunnable(UUID engineId) {
        return requireRunnable(engineId, true);
    }

    private ExecutionRoute requireRunnable(UUID engineId, boolean streamingRequired) {
        EngineSnapshot snapshot = Objects.requireNonNull(readTransaction.execute(status -> snapshot(engineId)));
        DispatcherInfoResponse info;
        DispatcherRegistrationResponse registration;
        try {
            String token = credentialCipher.decrypt(snapshot.accessTokenCiphertext());
            info = dispatcherClient.info(snapshot.dispatcherBaseUrl(), token);
            registration = dispatcherClient.registration(snapshot.dispatcherBaseUrl(), token);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Dispatcher 当前不可用，本次运行未创建", exception);
        }
        if (info == null || !Objects.equals(snapshot.dispatcherInstanceId(), info.dispatcherInstanceId())
                || snapshot.backendType() != info.backendType()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Dispatcher 身份或后端类型与计算引擎配置不一致");
        }
        if (streamingRequired && (info.capabilities() == null
                || !info.capabilities().streaming()
                || !info.capabilities().durableCheckpoint())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "计算引擎未上报 Streaming 或持久化 Checkpoint 能力");
        }
        if (registration == null
                || registration.state() != DispatcherRegistrationState.ACTIVE
                || !snapshot.id().equals(registration.engineId())
                || !Objects.equals(snapshot.dispatcherInstanceId(), registration.dispatcherInstanceId())
                || !sameRemoteConfiguration(snapshot, registration)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dispatcher 远端注册状态不是当前计算引擎的 ACTIVE 配置");
        }
        return new ExecutionRoute(
                snapshot.id(), snapshot.commandTopic(), snapshot.runnerEventTopic(), snapshot.adminEventTopic(),
                snapshot.maxQueuedExecutions(), snapshot.maxConcurrentSubmissions(),
                snapshot.maxInFlightApplications(), snapshot.backendType(), snapshot.dispatcherBaseUrl(),
                snapshot.accessTokenCiphertext(), snapshot.dispatcherInstanceId(), snapshot.resourcePolicy());
    }

    /** Must be invoked inside the final TaskRun/Outbox transaction. */
    public void assertUnchanged(ExecutionRoute expected) {
        ComputeEngine engine = repository.findByIdForUpdate(expected.engineId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "计算引擎已被删除，请重新运行"));
        requireRunnableState(engine);
        if (!engine.getCommandTopic().equals(expected.commandTopic())
                || !engine.getRunnerEventTopic().equals(expected.runnerEventTopic())
                || !engine.getAdminEventTopic().equals(expected.adminEventTopic())
                || engine.getMaxQueuedExecutions() != expected.maxQueuedExecutions()
                || engine.getMaxConcurrentSubmissions() != expected.maxConcurrentSubmissions()
                || engine.getMaxInFlightApplications() != expected.maxInFlightApplications()
                || !resourceConfigurationService.policy(engine.getResourcePolicyJson(), engine.getExpectedBackendType())
                .equals(expected.resourcePolicy())
                || engine.getExpectedBackendType() != expected.backendType()
                || !Objects.equals(engine.getDispatcherBaseUrl(), expected.dispatcherBaseUrl())
                || !Objects.equals(engine.getAccessTokenCiphertext(), expected.accessTokenCiphertext())
                || !Objects.equals(engine.getDispatcherInstanceId(), expected.dispatcherInstanceId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "计算引擎配置已变化，请重新运行");
        }
    }

    public String accessToken(ExecutionRoute route) {
        return credentialCipher.decrypt(route.accessTokenCiphertext());
    }

    /** Resolves missing values to the engine default and enforces the engine's per-run maximum. */
    public SparkExecutionResourceSpec resolveResources(
            ExecutionRoute route,
            SparkExecutionResourceSpec requested
    ) {
        SparkExecutionResourceSpec result = requested == null ? route.resourcePolicy().defaults() : requested;
        if (result.exceeds(route.resourcePolicy().maximums())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务运行资源超过计算引擎单次任务上限");
        }
        return result;
    }

    public DispatcherExecutionResponse execution(UUID engineId, UUID executionId) {
        EngineSnapshot snapshot = Objects.requireNonNull(readTransaction.execute(status -> rawSnapshot(engineId)));
        DispatcherExecutionResponse response = dispatcherClient.execution(
                snapshot.dispatcherBaseUrl(), credentialCipher.decrypt(snapshot.accessTokenCiphertext()), executionId);
        if (response == null || !engineId.equals(response.engineId()) || !executionId.equals(response.executionId())) {
            throw new IllegalStateException("Dispatcher 返回了不匹配的执行身份");
        }
        return response;
    }

    public DispatcherExecutionLogResponse executionLog(UUID engineId, UUID executionId, int attempt) {
        EngineSnapshot snapshot = Objects.requireNonNull(readTransaction.execute(status -> rawSnapshot(engineId)));
        try {
            DispatcherExecutionLogResponse response = dispatcherClient.executionLog(
                    snapshot.dispatcherBaseUrl(), credentialCipher.decrypt(snapshot.accessTokenCiphertext()),
                    executionId, attempt);
            if (response == null || !engineId.equals(response.engineId())
                    || !executionId.equals(response.executionId()) || attempt != response.attempt()) {
                throw new IllegalStateException("Dispatcher 返回了不匹配的日志执行身份");
            }
            return response;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Dispatcher 当前无法提供运行日志", exception);
        }
    }

    private EngineSnapshot snapshot(UUID engineId) {
        EngineSnapshot snapshot = rawSnapshot(engineId);
        ComputeEngine engine = repository.findById(engineId).orElseThrow();
        requireRunnableState(engine);
        return snapshot;
    }

    private EngineSnapshot rawSnapshot(UUID engineId) {
        if (engineId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark Canvas 任务必须选择计算引擎");
        }
        ComputeEngine engine = repository.findById(engineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "计算引擎不存在"));
        return new EngineSnapshot(
                engine.getId(), engine.getCommandTopic(), engine.getRunnerEventTopic(), engine.getAdminEventTopic(),
                engine.getMaxQueuedExecutions(), engine.getMaxConcurrentSubmissions(),
                engine.getMaxInFlightApplications(), engine.getExpectedBackendType(),
                engine.getDispatcherBaseUrl(), engine.getAccessTokenCiphertext(), engine.getDispatcherInstanceId(),
                resourceConfigurationService.policy(engine.getResourcePolicyJson(), engine.getExpectedBackendType()),
                engine.getResourcePolicyJson());
    }

    private boolean sameRemoteConfiguration(
            EngineSnapshot snapshot,
            DispatcherRegistrationResponse registration
    ) {
        var topics = registration.topics();
        var policy = registration.effectiveAdmissionPolicy();
        return registration.backendType() == snapshot.backendType()
                && topics != null
                && Objects.equals(topics.commandTopic(), snapshot.commandTopic())
                && Objects.equals(topics.runnerEventTopic(), snapshot.runnerEventTopic())
                && Objects.equals(topics.adminEventTopic(), snapshot.adminEventTopic())
                && Objects.equals(topics.runnerControlTopic(), snapshot.runnerEventTopic() + ".control")
                && policy != null
                && policy.maxQueuedExecutions() == snapshot.maxQueuedExecutions()
                && policy.maxConcurrentSubmissions() == snapshot.maxConcurrentSubmissions()
                && policy.maxInFlightApplications() == snapshot.maxInFlightApplications()
                && Objects.equals(registration.resourcePolicy(), snapshot.resourcePolicy());
    }

    private static void requireRunnableState(ComputeEngine engine) {
        if (engine.getRegistrationState() != ComputeEngineRegistrationState.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "计算引擎未激活：" + engine.getName());
        }
        if (engine.getHealthState() != ComputeEngineHealthState.UP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "计算引擎健康状态不是 UP：" + engine.getName());
        }
        if (engine.getReportedBackendType() != engine.getExpectedBackendType()
                || engine.getDispatcherInstanceId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "计算引擎注册信息不完整：" + engine.getName());
        }
    }

    public record ExecutionRoute(
            UUID engineId,
            String commandTopic,
            String runnerEventTopic,
            String adminEventTopic,
            int maxQueuedExecutions,
            int maxConcurrentSubmissions,
            int maxInFlightApplications,
            ComputeBackendType backendType,
            String dispatcherBaseUrl,
            String accessTokenCiphertext,
            String dispatcherInstanceId,
            SparkExecutionResourcePolicy resourcePolicy
    ) {
    }

    private record EngineSnapshot(
            UUID id,
            String commandTopic,
            String runnerEventTopic,
            String adminEventTopic,
            int maxQueuedExecutions,
            int maxConcurrentSubmissions,
            int maxInFlightApplications,
            ComputeBackendType backendType,
            String dispatcherBaseUrl,
            String accessTokenCiphertext,
            String dispatcherInstanceId,
            SparkExecutionResourcePolicy resourcePolicy,
            String resourcePolicyJson
    ) {
    }
}
