package cn.superhuang.data.scalpel.dispatcher.service;

import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.data.scalpel.contract.execution.RunnerEventChannel;
import cn.superhuang.data.scalpel.contract.execution.RunnerControlChannel;
import cn.superhuang.data.scalpel.contract.execution.SafeExecutionError;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendStatus;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendSubmission;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionIdentity;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionLaunch;
import cn.superhuang.data.scalpel.dispatcher.backend.ExternalExecutionHandle;
import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherTaskResult;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherRunnerKafkaProperties;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherStreamingProperties;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionState;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistrationState;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherRegistrationRepository;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DispatcherExecutionStateService {
    private static final List<DispatcherExecutionState> IN_FLIGHT = List.of(
            DispatcherExecutionState.SUBMITTING, DispatcherExecutionState.SUBMITTED,
            DispatcherExecutionState.RUNNING, DispatcherExecutionState.CANCEL_REQUESTED
    );

    private final DispatcherTaskExecutionRepository executionRepository;
    private final DispatcherRegistrationRepository registrationRepository;
    private final DispatcherEventService eventService;
    private final DispatcherRunnerKafkaProperties runnerKafkaProperties;
    private final DispatcherStreamingProperties streamingProperties;

    public DispatcherExecutionStateService(
            DispatcherTaskExecutionRepository executionRepository,
            DispatcherRegistrationRepository registrationRepository,
            DispatcherEventService eventService,
            DispatcherRunnerKafkaProperties runnerKafkaProperties,
            DispatcherStreamingProperties streamingProperties
    ) {
        this.executionRepository = executionRepository;
        this.registrationRepository = registrationRepository;
        this.eventService = eventService;
        this.runnerKafkaProperties = runnerKafkaProperties;
        this.streamingProperties = streamingProperties;
    }

    @Transactional
    public Optional<ExecutionLaunch> claimNext() {
        List<DispatcherRegistration> registrations = registrationRepository.findAllForUpdate();
        if (registrations.isEmpty() || registrations.getFirst().getState() != DispatcherRegistrationState.ACTIVE) {
            return Optional.empty();
        }
        DispatcherRegistration registration = registrations.getFirst();
        if (executionRepository.countByState(DispatcherExecutionState.SUBMITTING)
                >= registration.getMaxConcurrentSubmissions()) return Optional.empty();
        if (registration.getMaxInFlightApplications() > 0
                && executionRepository.countByStateIn(IN_FLIGHT) >= registration.getMaxInFlightApplications()) {
            return Optional.empty();
        }
        List<DispatcherTaskExecution> queued = executionRepository.findQueuedForUpdate(PageRequest.of(0, 1));
        if (queued.isEmpty()) return Optional.empty();
        DispatcherTaskExecution execution = queued.getFirst();
        if (execution.getDeadlineAt() != null && !execution.getDeadlineAt().isAfter(Instant.now())) {
            SafeExecutionError error = dispatcherError(
                    "EXECUTION_TIMEOUT", "执行在排队期间超过 Deadline", ExecutionErrorCategory.TIMEOUT, false);
            execution.timedOut(error);
            executionRepository.save(execution);
            eventService.enqueue(execution, ExecutionMessageType.EXECUTION_TIMED_OUT, error, null);
            return Optional.empty();
        }
        execution.beginSubmission();
        executionRepository.save(execution);
        return Optional.of(launch(
                execution, registration, runnerKafkaProperties, streamingProperties));
    }

    @Transactional
    public void submitted(UUID executionId, BackendSubmission submission) {
        DispatcherTaskExecution execution = locked(executionId);
        execution.submitted(submission.handle().externalId(), submission.handle().trackingUrl());
        executionRepository.save(execution);
        eventService.enqueue(execution, ExecutionMessageType.EXECUTION_SUBMITTED, null, null);
    }

    @Transactional
    public void submissionFailed(UUID executionId, String code, String message) {
        DispatcherTaskExecution execution = locked(executionId);
        SafeExecutionError error = dispatcherError(
                safeCode(code), safeMessage(message), ExecutionErrorCategory.EXTERNAL_SYSTEM, true);
        execution.fail(error);
        executionRepository.save(execution);
        eventService.enqueue(execution, ExecutionMessageType.EXECUTION_FAILED, error, null);
    }

    @Transactional
    public void applyObservation(UUID executionId, BackendStatus status) {
        DispatcherTaskExecution execution = locked(executionId);
        if (execution.getState().terminal()) return;
        execution.observeTrackingUrl(status.trackingUrl());
        switch (status.state()) {
            case PENDING -> execution.observed();
            case RUNNING -> {
                boolean first = execution.getState() != DispatcherExecutionState.RUNNING;
                execution.running(status.startedAt());
                if (first && execution.getState() == DispatcherExecutionState.RUNNING) {
                    eventService.enqueue(execution, ExecutionMessageType.EXECUTION_RUNNING, null, null);
                }
            }
            case SUCCEEDED -> {
                execution.succeed();
                eventService.enqueue(execution, ExecutionMessageType.EXECUTION_SUCCEEDED, null, null);
            }
            case FAILED -> {
                String code = safeCode(status.safeErrorCode());
                String message = safeMessage(status.safeErrorMessage());
                SafeExecutionError error = dispatcherError(
                        code, message, ExecutionErrorCategory.EXTERNAL_SYSTEM, true);
                execution.fail(error);
                eventService.enqueue(execution, ExecutionMessageType.EXECUTION_FAILED, error, null);
            }
            case CANCELLED -> {
                SafeExecutionError error = dispatcherError(
                        "EXECUTION_CANCELLED", "Backend 已取消执行", ExecutionErrorCategory.CANCELLED, false);
                execution.cancelled(error);
                eventService.enqueue(execution, ExecutionMessageType.EXECUTION_CANCELLED, error, null);
            }
            case UNKNOWN -> execution.observed();
        }
        executionRepository.save(execution);
    }

    @Transactional
    public void cancelled(UUID executionId) {
        DispatcherTaskExecution execution = locked(executionId);
        if (execution.getState().terminal()) return;
        SafeExecutionError error = execution.isForceTerminateRequested()
                ? dispatcherError("EXECUTION_FORCE_TERMINATED", "执行已被强制终止",
                        ExecutionErrorCategory.CANCELLED, false)
                : dispatcherError("EXECUTION_CANCELLED", "执行已取消",
                        ExecutionErrorCategory.CANCELLED, false);
        execution.cancelled(error);
        executionRepository.save(execution);
        eventService.enqueue(execution, ExecutionMessageType.EXECUTION_CANCELLED, error, null);
    }

    @Transactional
    public void forceTerminationUnconfirmed(UUID executionId) {
        DispatcherTaskExecution execution = locked(executionId);
        if (execution.getState().terminal()) return;
        SafeExecutionError error = dispatcherError(
                "EXECUTION_TERMINATION_UNCONFIRMED",
                "强制终止后仍无法确认 Backend 终态，平台已停止跟踪该执行",
                ExecutionErrorCategory.EXTERNAL_SYSTEM,
                false
        );
        execution.lost(error);
        executionRepository.save(execution);
        eventService.enqueue(execution, ExecutionMessageType.EXECUTION_LOST, error, null);
    }

    @Transactional
    public void timedOut(UUID executionId) {
        DispatcherTaskExecution execution = locked(executionId);
        if (execution.getState().terminal()) return;
        SafeExecutionError error = dispatcherError(
                "EXECUTION_TIMEOUT", "执行超过 Deadline", ExecutionErrorCategory.TIMEOUT, false);
        execution.timedOut(error);
        executionRepository.save(execution);
        eventService.enqueue(execution, ExecutionMessageType.EXECUTION_TIMED_OUT, error, null);
    }

    @Transactional
    public void recovered(UUID executionId, ExternalExecutionHandle handle) {
        DispatcherTaskExecution execution = locked(executionId);
        if (execution.getState() != DispatcherExecutionState.SUBMITTING) return;
        execution.submitted(handle.externalId(), handle.trackingUrl());
        executionRepository.save(execution);
        eventService.enqueue(execution, ExecutionMessageType.EXECUTION_SUBMITTED, null, null);
    }

    @Transactional
    public void lost(UUID executionId, String message) {
        DispatcherTaskExecution execution = locked(executionId);
        if (execution.getState().terminal()) return;
        SafeExecutionError error = dispatcherError(
                "EXECUTION_LOST", safeMessage(message), ExecutionErrorCategory.EXTERNAL_SYSTEM, true);
        execution.lost(error);
        executionRepository.save(execution);
        eventService.enqueue(execution, ExecutionMessageType.EXECUTION_LOST, error, null);
    }

    @Transactional
    public void awaitingRunnerResult(UUID executionId, Instant backendEndedAt) {
        DispatcherTaskExecution execution = locked(executionId);
        if (execution.getState().terminal()) return;
        execution.observed();
        execution.awaitingResult(backendEndedAt);
        executionRepository.save(execution);
    }

    @Transactional
    public void runnerResultMissing(UUID executionId) {
        DispatcherTaskExecution execution = locked(executionId);
        if (execution.getState().terminal()) return;
        String code = "RUNNER_RESULT_MISSING";
        String message = "Runner 已退出但未生成 result.json";
        SafeExecutionError error = dispatcherError(
                code, message, ExecutionErrorCategory.EXTERNAL_SYSTEM, true);
        execution.fail(error);
        executionRepository.save(execution);
        eventService.enqueue(execution, ExecutionMessageType.EXECUTION_FAILED, error, null);
    }

    @Transactional
    public void runnerResultInvalid(UUID executionId, String reason) {
        DispatcherTaskExecution execution = locked(executionId);
        if (execution.getState().terminal()) return;
        String code = "INVALID_RUNNER_RESULT";
        String message = safeMessage(reason == null ? "Runner result.json 无效" : reason);
        SafeExecutionError error = dispatcherError(
                code, message, ExecutionErrorCategory.INTERNAL, false);
        execution.fail(error);
        executionRepository.save(execution);
        eventService.enqueue(execution, ExecutionMessageType.EXECUTION_FAILED, error, null);
    }

    @Transactional
    public void applyRunnerResult(UUID executionId, DispatcherTaskResult result, String resultSha256) {
        DispatcherTaskExecution execution = locked(executionId);
        if (execution.getState().terminal()) return;
        execution.recordResultSha256(resultSha256);
        Long affectedRows = result.affectedRows();
        switch (result.state()) {
            case SUCCESS -> {
                cn.superhuang.data.scalpel.contract.quality.QualitySummary qualitySummary =
                        result.qualityResult() == null ? null
                                : new cn.superhuang.data.scalpel.contract.quality.QualitySummary(
                                result.qualityResult().conclusion(), result.qualityResult().totalRules(),
                                result.qualityResult().passedRules(), result.qualityResult().failedRules(),
                                result.qualityResult().skippedRules(), result.qualityResult().checkedRows());
                execution.completeFromRunner(DispatcherExecutionState.SUCCESS, result.startedAt(), result.endedAt(),
                        affectedRows, null);
                execution.applyQualitySummary(qualitySummary);
                executionRepository.save(execution);
                eventService.enqueueTerminal(execution, ExecutionMessageType.EXECUTION_SUCCEEDED, null,
                        affectedRows, qualitySummary, result.userJobObservability());
            }
            case STOPPED -> {
                execution.completeFromRunner(DispatcherExecutionState.STOPPED,
                        result.startedAt(), result.endedAt(), affectedRows, null);
                executionRepository.save(execution);
                eventService.enqueueTerminal(execution, ExecutionMessageType.EXECUTION_STOPPED,
                        null, affectedRows, null, result.userJobObservability());
            }
            case FAILED -> {
                SafeExecutionError safeError = safeError(result.error());
                execution.completeFromRunner(DispatcherExecutionState.FAILED, result.startedAt(), result.endedAt(),
                        affectedRows, safeError);
                executionRepository.save(execution);
                eventService.enqueueTerminal(execution, ExecutionMessageType.EXECUTION_FAILED,
                        safeError, affectedRows, null, result.userJobObservability());
            }
            case TIMED_OUT -> {
                SafeExecutionError safeError = safeError(result.error());
                execution.completeFromRunner(DispatcherExecutionState.TIMED_OUT, result.startedAt(), result.endedAt(),
                        affectedRows, safeError);
                executionRepository.save(execution);
                eventService.enqueueTerminal(execution, ExecutionMessageType.EXECUTION_TIMED_OUT,
                        safeError, affectedRows, null, result.userJobObservability());
            }
            case CANCELLED -> {
                SafeExecutionError safeError = safeError(result.error());
                execution.completeFromRunner(DispatcherExecutionState.CANCELLED, result.startedAt(), result.endedAt(),
                        affectedRows, safeError);
                executionRepository.save(execution);
                eventService.enqueueTerminal(execution, ExecutionMessageType.EXECUTION_CANCELLED,
                        safeError, affectedRows, null, result.userJobObservability());
            }
            default -> throw new IllegalArgumentException("Runner result.json 不是终态");
        }
    }

    @Transactional
    public void logArtifactStored(UUID executionId) {
        DispatcherTaskExecution execution = locked(executionId);
        execution.logArtifactStored();
        executionRepository.save(execution);
    }

    @Transactional
    public void externalCleanupCompleted(UUID executionId) {
        DispatcherTaskExecution execution = locked(executionId);
        execution.externalCleanupCompleted();
        executionRepository.save(execution);
    }

    @Transactional
    public void observationFailed(UUID executionId) {
        DispatcherTaskExecution execution = locked(executionId);
        if (execution.getState().terminal()) return;
        execution.observationFailed();
        executionRepository.save(execution);
    }

    private DispatcherTaskExecution locked(UUID executionId) {
        DispatcherTaskExecution found = executionRepository.findByExecutionId(executionId)
                .orElseThrow(() -> new IllegalStateException("执行账本不存在"));
        return executionRepository.findByIdForUpdate(found.getId()).orElseThrow();
    }

    static ExecutionLaunch launch(
            DispatcherTaskExecution execution,
            DispatcherRegistration registration,
            DispatcherRunnerKafkaProperties kafka,
            DispatcherStreamingProperties streaming
    ) {
        boolean streamingExecution = execution.getStreamingDeploymentId() != null;
        return new ExecutionLaunch(
                new ExecutionIdentity(execution.getEngineId(), execution.getExecutionId(), execution.getRunId(), execution.getAttempt()),
                execution.getManifestKey(), execution.getManifestSha256(), execution.getResultKey(),
                execution.getLogKey(), execution.getDeadlineAt(),
                new RunnerEventChannel(
                        kafka.bootstrapServers(), registration.getRunnerEventTopic(), kafka.securityProtocol(),
                        kafka.clientIdPrefix() + "-" + execution.getExecutionId()),
                streamingExecution
                        ? streaming.checkpointUri(execution.getCheckpointKeyPrefix())
                        : null,
                streamingExecution
                        ? new RunnerControlChannel(
                                kafka.bootstrapServers(),
                                registration.getRunnerControlTopic(),
                                kafka.securityProtocol(),
                                kafka.clientIdPrefix() + "-control-" + execution.getExecutionId(),
                                "datascalpel-runner-control-" + execution.getExecutionId()
                                    + "-" + execution.getAttempt())
                        : null,
                execution.getQualitySampleRuleIds(), execution.getUserJar(), execution.getSparkConf(),
                execution.getExecutionResources()
        );
    }

    static ExecutionLaunch launch(
            DispatcherTaskExecution execution,
            DispatcherRegistration registration,
            DispatcherRunnerKafkaProperties kafka
    ) {
        return launch(
                execution, registration, kafka,
                new DispatcherStreamingProperties(null));
    }

    static String safeCode(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,99}")) return "BACKEND_FAILED";
        return value;
    }

    static String safeMessage(String value) {
        String safe = value == null || value.isBlank() ? "Backend 执行失败" : value.trim();
        safe = safe.replaceAll("(?i)(password|secret|token)=[^\\s,;]+", "$1=***");
        return safe.substring(0, Math.min(1000, safe.length()));
    }

    private static SafeExecutionError safeError(DispatcherTaskResult.Error error) {
        return new SafeExecutionError(
                safeCode(error.code()), safeMessage(error.message()), error.category(), error.retryable(),
                error.nodeId(), error.nodeType(), error.nodeName(), error.phase(), error.sqlState(),
                error.diagnosticId());
    }

    private static SafeExecutionError dispatcherError(
            String code,
            String message,
            ExecutionErrorCategory category,
            boolean retryable
    ) {
        return new SafeExecutionError(
                code, message, category, retryable, null, null, null,
                ExecutionFailurePhase.DISPATCH, null, UUID.randomUUID());
    }
}
