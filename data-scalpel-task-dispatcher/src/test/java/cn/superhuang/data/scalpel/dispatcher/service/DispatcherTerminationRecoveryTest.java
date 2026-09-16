package cn.superhuang.data.scalpel.dispatcher.service;

import cn.superhuang.data.scalpel.contract.execution.*;
import cn.superhuang.data.scalpel.dispatcher.artifact.*;
import cn.superhuang.data.scalpel.dispatcher.backend.*;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherArtifactProperties;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherProperties;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DispatcherTerminationRecoveryTest {
    private final TaskExecutionBackend backend = mock(TaskExecutionBackend.class);
    private final DispatcherExecutionStateService states = mock(DispatcherExecutionStateService.class);
    private final DispatcherTaskExecutionRepository executions = mock(DispatcherTaskExecutionRepository.class);
    private final DispatcherProperties properties = mock(DispatcherProperties.class);
    private final DispatcherExecutionCoordinator coordinator = new DispatcherExecutionCoordinator(
            backend, states, executions, properties, mock(DispatcherArtifactProperties.class),
            mock(DispatcherResultService.class), mock(DispatcherArtifactService.class));

    @Test
    void retriesFailedCancellationAfterReportingTimeout() throws Exception {
        var execution = execution();
        execution.submitted("external-1", null);
        arrange(execution);
        when(backend.inspect(any(), any())).thenReturn(status(BackendExecutionState.RUNNING),
                status(BackendExecutionState.RUNNING), status(BackendExecutionState.CANCELLED));
        doThrow(new BackendException("UNAVAILABLE", "temporary outage")).doNothing()
                .when(backend).cancel(any(), any());

        coordinator.observe();
        assertThat(execution.getState()).isEqualTo(DispatcherExecutionState.TIMED_OUT);
        coordinator.cleanup();
        assertThat(execution.isExternalCleanupCompleted()).isFalse();
        coordinator.cleanup();
        assertThat(execution.isExternalCleanupCompleted()).isTrue();
        verify(backend, times(2)).cancel(any(), any());
        assertThat(execution.getState()).isEqualTo(DispatcherExecutionState.TIMED_OUT);
    }

    @Test
    void recoversAndTerminatesAnUncertainSubmissionAfterTimeout() throws Exception {
        var execution = execution();
        arrange(execution);
        var handle = new ExternalExecutionHandle(ExecutionBackendType.LOCAL_DOCKER, "recovered", null);
        when(backend.recover(any())).thenReturn(Optional.of(handle));
        when(backend.inspect(any(), any())).thenReturn(status(BackendExecutionState.RUNNING),
                status(BackendExecutionState.CANCELLED));
        doAnswer(call -> {
            execution.attachExternalHandle(handle.externalId(), null);
            return null;
        }).when(states).recovered(execution.getExecutionId(), handle);

        coordinator.observe();
        coordinator.cleanup();
        verify(backend).recover(any());
        verify(backend).cancel(eq(handle), any());
        assertThat(execution.getExternalExecutionId()).isEqualTo("recovered");
        assertThat(execution.isExternalCleanupCompleted()).isTrue();
        assertThat(execution.getState()).isEqualTo(DispatcherExecutionState.TIMED_OUT);
    }

    @Test
    void lateSubmissionResponseKeepsTimeoutAndReopensCleanup() {
        var execution = execution();
        execution.timedOut("deadline");
        execution.externalCleanupCompleted();
        execution.submitted("late-handle", null);
        assertThat(execution.getState()).isEqualTo(DispatcherExecutionState.TIMED_OUT);
        assertThat(execution.getExternalExecutionId()).isEqualTo("late-handle");
        assertThat(execution.isExternalCleanupCompleted()).isFalse();
    }

    @Test
    void cleanupRetriesBackOffToFiveMinutesWithoutExceedingTheCap() throws Exception {
        var execution = execution();
        execution.submitted("external-1", null);
        execution.timedOut("deadline");
        arrange(execution);
        when(backend.inspect(any(), any())).thenReturn(status(BackendExecutionState.RUNNING));
        for (int attempts : new int[]{0, 1, 6, 40}) {
            ReflectionTestUtils.setField(execution, "cleanupAttempts", attempts);
            clearInvocations(states);
            Instant before = Instant.now();
            coordinator.cleanup();
            Instant after = Instant.now();
            long expectedSeconds = Math.min(300, 5L << Math.min(attempts, 6));
            var next = org.mockito.ArgumentCaptor.forClass(Instant.class);
            verify(states).scheduleMaintenance(eq(execution.getExecutionId()), next.capture(), eq(true));
            assertThat(next.getValue()).isBetween(before.plusSeconds(expectedSeconds), after.plusSeconds(expectedSeconds));
        }
    }

    private void arrange(DispatcherTaskExecution execution) throws Exception {
        when(properties.observationPollInterval()).thenReturn(Duration.ofSeconds(5));
        when(properties.submissionUncertainGrace()).thenReturn(Duration.ofSeconds(1));
        when(backend.type()).thenReturn(ExecutionBackendType.LOCAL_DOCKER);
        when(backend.collectLog(any())).thenReturn(new BackendLog(new byte[0], false));
        when(executions.findDueObservations(any(), any(), any())).thenReturn(List.of(execution));
        when(executions.findDueCleanup(any(), any(), any())).thenReturn(List.of(execution));
        doAnswer(call -> { execution.timedOut("deadline"); return null; })
                .when(states).timedOut(execution.getExecutionId());
        doAnswer(call -> { execution.externalCleanupCompleted(); return null; })
                .when(states).externalCleanupCompleted(execution.getExecutionId());
    }

    private static BackendStatus status(BackendExecutionState state) {
        return new BackendStatus(state, null, null, null, null);
    }

    private static DispatcherTaskExecution execution() {
        UUID runId = UUID.randomUUID();
        String prefix = "task-runs/" + runId + "/attempts/1/";
        Instant before = Instant.now().minusSeconds(20);
        var command = new SubmitExecutionCommand(1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION,
                before, UUID.randomUUID(), UUID.randomUUID(), runId, 1, UUID.randomUUID(),
                ExecutionTaskType.SPARK_CANVAS, 1, before.plusSeconds(1),
                new ExecutionArtifactLocation(prefix + "manifest.json", "a".repeat(64),
                        prefix + "result.json", prefix + "console.log"));
        var execution = DispatcherTaskExecution.queue(command, "b".repeat(64), ExecutionBackendType.LOCAL_DOCKER,
                SparkExecutionResourcePolicy.defaultsFor(ExecutionBackendType.LOCAL_DOCKER).defaults());
        execution.beginSubmission();
        ReflectionTestUtils.setField(execution, "submissionStartedAt", before);
        return execution;
    }
}
