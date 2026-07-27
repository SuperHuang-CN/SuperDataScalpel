package cn.superhuang.data.scalpel.dispatcher.service;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendExecutionState;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendStatus;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionIdentity;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionLaunch;
import cn.superhuang.data.scalpel.dispatcher.backend.ExternalExecutionHandle;
import cn.superhuang.data.scalpel.dispatcher.backend.TaskExecutionBackend;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherProperties;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherArtifactProperties;
import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherResultResolution;
import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherResultService;
import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherArtifactService;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherExecutionState;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class DispatcherExecutionCoordinator {
    private static final List<DispatcherExecutionState> OBSERVED = List.of(
            DispatcherExecutionState.QUEUED, DispatcherExecutionState.SUBMITTING, DispatcherExecutionState.SUBMITTED,
            DispatcherExecutionState.RUNNING, DispatcherExecutionState.CANCEL_REQUESTED
    );
    private static final List<DispatcherExecutionState> TERMINAL = List.of(
            DispatcherExecutionState.SUCCESS, DispatcherExecutionState.FAILED,
            DispatcherExecutionState.TIMED_OUT, DispatcherExecutionState.CANCELLED,
            DispatcherExecutionState.STOPPED, DispatcherExecutionState.LOST
    );

    private final TaskExecutionBackend backend;
    private final DispatcherExecutionStateService stateService;
    private final DispatcherTaskExecutionRepository executionRepository;
    private final DispatcherProperties properties;
    private final DispatcherArtifactProperties artifactProperties;
    private final DispatcherResultService resultService;
    private final DispatcherArtifactService artifactService;

    public DispatcherExecutionCoordinator(
            TaskExecutionBackend backend,
            DispatcherExecutionStateService stateService,
            DispatcherTaskExecutionRepository executionRepository,
            DispatcherProperties properties,
            DispatcherArtifactProperties artifactProperties,
            DispatcherResultService resultService,
            DispatcherArtifactService artifactService
    ) {
        this.backend = backend;
        this.stateService = stateService;
        this.executionRepository = executionRepository;
        this.properties = properties;
        this.artifactProperties = artifactProperties;
        this.resultService = resultService;
        this.artifactService = artifactService;
    }

    @Scheduled(fixedDelayString = "${data-scalpel.dispatcher.admission-poll-interval:500ms}")
    public void admit() {
        Optional<ExecutionLaunch> launch = stateService.claimNext();
        if (launch.isEmpty()) return;
        try {
            stateService.submitted(launch.get().identity().executionId(), backend.submit(launch.get()));
        } catch (BackendException exception) {
            if ("BACKEND_SUBMISSION_UNCERTAIN".equals(exception.code())) return;
            stateService.submissionFailed(
                    launch.get().identity().executionId(),
                    DispatcherExecutionStateService.safeCode(exception.code()),
                    DispatcherExecutionStateService.safeMessage(exception.getMessage())
            );
        }
    }

    @Scheduled(fixedDelayString = "${data-scalpel.dispatcher.observation-poll-interval:5s}")
    public void observe() {
        for (DispatcherTaskExecution execution : executionRepository.findAllByStateIn(OBSERVED)) {
            observe(execution);
        }
        for (DispatcherTaskExecution execution : executionRepository.findAllByStateIn(TERMINAL)) {
            finalizeExternalExecution(execution);
        }
    }

    private void finalizeExternalExecution(DispatcherTaskExecution execution) {
        if (execution.getExternalExecutionId() == null || execution.isExternalCleanupCompleted()) return;
        ExternalExecutionHandle handle = handle(execution);
        try {
            BackendStatus status = backend.inspect(handle, identity(execution));
            if (status.state() != BackendExecutionState.SUCCEEDED
                    && status.state() != BackendExecutionState.FAILED
                    && status.state() != BackendExecutionState.CANCELLED
                    && status.state() != BackendExecutionState.UNKNOWN) return;
            if (!execution.isLogArtifactStored() && status.state() != BackendExecutionState.UNKNOWN) {
                var log = backend.collectLog(handle);
                artifactService.store(execution.getLogKey(), log.content(), "text/plain; charset=utf-8");
                stateService.logArtifactStored(execution.getExecutionId());
            }
            backend.cleanup(handle, identity(execution));
            stateService.externalCleanupCompleted(execution.getExecutionId());
        } catch (BackendException ignored) {
            // Finalization is idempotent and retried by the next observation cycle.
        }
    }

    private void observe(DispatcherTaskExecution execution) {
        if (execution.getDeadlineAt() != null && Instant.now().isAfter(execution.getDeadlineAt())) {
            cancelBestEffort(execution);
            stateService.timedOut(execution.getExecutionId());
            return;
        }
        if (execution.getState() == DispatcherExecutionState.SUBMITTING && execution.getExternalExecutionId() == null) {
            recoverSubmission(execution);
            return;
        }
        if (execution.getExternalExecutionId() == null) return;
        ExternalExecutionHandle handle = handle(execution);
        try {
            if (execution.getState() == DispatcherExecutionState.CANCEL_REQUESTED || execution.isCancelRequested()) {
                if (execution.isStreamingStopRequested()
                        && execution.getStreamingForceStopAt() != null
                        && execution.getStreamingForceStopAt().isAfter(Instant.now())) {
                    /*
                     * The control command is already durably queued. Keep the
                     * Backend alive during the graceful window so the Runner
                     * can finish the current micro-batch and emit STOPPED.
                     */
                    return;
                }
                cancelAndConfirm(execution, handle);
            } else {
                BackendStatus status = backend.inspect(handle, identity(execution));
                if (status.state() == BackendExecutionState.SUCCEEDED
                        || status.state() == BackendExecutionState.FAILED) {
                    reconcileTerminal(execution, status);
                } else if (status.state() == BackendExecutionState.CANCELLED) {
                    storeTerminalLog(execution, handle, status);
                    stateService.applyObservation(execution.getExecutionId(), status);
                } else {
                    stateService.applyObservation(execution.getExecutionId(), status);
                }
            }
        } catch (BackendException exception) {
            recordObservationFailure(execution, exception);
        }
    }

    private void cancelAndConfirm(DispatcherTaskExecution execution, ExternalExecutionHandle handle)
            throws BackendException {
        ExecutionIdentity identity = identity(execution);
        BackendStatus before = backend.inspect(handle, identity);
        if (before.state() == BackendExecutionState.SUCCEEDED || before.state() == BackendExecutionState.FAILED) {
            reconcileTerminal(execution, before);
            return;
        }
        if (before.state() == BackendExecutionState.CANCELLED) {
            storeTerminalLog(execution, handle, before);
            stateService.cancelled(execution.getExecutionId());
            return;
        }

        backend.cancel(handle, identity);
        BackendStatus after = backend.inspect(handle, identity);
        if (after.state() == BackendExecutionState.PENDING || after.state() == BackendExecutionState.RUNNING) return;
        // A confirmed terminal resource or an absent resource after a successful cancel
        // means the cancellation request won. This avoids declaring CANCELLED merely
        // because a kill/delete command was accepted asynchronously.
        storeTerminalLog(execution, handle, after);
        stateService.cancelled(execution.getExecutionId());
    }

    private void reconcileTerminal(DispatcherTaskExecution execution, BackendStatus backendStatus)
            throws BackendException {
        storeTerminalLog(execution, handle(execution), backendStatus);
        DispatcherResultResolution resolution = resultService.reconcile(execution.getExecutionId(), null);
        if (resolution != DispatcherResultResolution.NOT_FOUND) return;
        if (execution.getResultAwaitingSince() == null) {
            stateService.awaitingRunnerResult(execution.getExecutionId(), backendStatus.endedAt());
            return;
        }
        if (execution.getResultAwaitingSince().plus(artifactProperties.resultAvailabilityGrace()).isAfter(Instant.now())) {
            return;
        }
        if (backendStatus.state() == BackendExecutionState.FAILED) {
            stateService.applyObservation(execution.getExecutionId(), backendStatus);
        } else {
            stateService.runnerResultMissing(execution.getExecutionId());
        }
    }

    private void storeTerminalLog(
            DispatcherTaskExecution execution,
            ExternalExecutionHandle handle,
            BackendStatus status
    ) throws BackendException {
        if (execution.isLogArtifactStored() || status.state() == BackendExecutionState.UNKNOWN) return;
        var log = backend.collectLog(handle);
        artifactService.store(execution.getLogKey(), log.content(), "text/plain; charset=utf-8");
        stateService.logArtifactStored(execution.getExecutionId());
    }

    private void recoverSubmission(DispatcherTaskExecution execution) {
        if (execution.getSubmissionStartedAt() == null
                || execution.getSubmissionStartedAt().plus(properties.submissionUncertainGrace()).isAfter(Instant.now())) return;
        try {
            Optional<ExternalExecutionHandle> recovered = backend.recover(identity(execution));
            if (recovered.isPresent()) stateService.recovered(execution.getExecutionId(), recovered.get());
            else {
                backend.cleanup(identity(execution));
                stateService.lost(execution.getExecutionId(), "提交结果不确定且 Backend 未找到对应执行");
            }
        } catch (BackendException exception) {
            recordObservationFailure(execution, exception);
        }
    }

    private void recordObservationFailure(DispatcherTaskExecution execution, BackendException exception) {
        Instant failureSince = execution.getObservationFailureSince();
        if (failureSince != null
                && !failureSince.plus(properties.observationFailureGrace()).isAfter(Instant.now())) {
            stateService.lost(execution.getExecutionId(),
                    "Backend 持续不可观测：" + DispatcherExecutionStateService.safeMessage(exception.getMessage()));
            return;
        }
        stateService.observationFailed(execution.getExecutionId());
    }

    private void cancelBestEffort(DispatcherTaskExecution execution) {
        if (execution.getExternalExecutionId() == null) return;
        try { backend.cancel(handle(execution), identity(execution)); } catch (BackendException ignored) { }
    }

    private ExternalExecutionHandle handle(DispatcherTaskExecution execution) {
        return new ExternalExecutionHandle(backend.type(), execution.getExternalExecutionId(), execution.getTrackingUrl());
    }

    private static ExecutionIdentity identity(DispatcherTaskExecution execution) {
        return new ExecutionIdentity(
                execution.getEngineId(), execution.getExecutionId(), execution.getRunId(), execution.getAttempt()
        );
    }
}
