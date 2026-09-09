package cn.superhuang.data.scalpel.business.task.execution.service;

import cn.superhuang.data.scalpel.business.compute.client.DispatcherExecutionResponse;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingDeployment;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingQuery;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskStreamingDeploymentRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskStreamingQueryRepository;
import cn.superhuang.data.scalpel.business.task.service.CanvasTaskRunProperties;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatcherExecutionReconciliationServiceTest {

    @Test
    void mapsStoppedStreamingLedgerToStoppedAdminEvent() {
        UUID executionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID engineId = UUID.randomUUID();
        UUID deploymentId = UUID.randomUUID();
        Instant endedAt = Instant.now();
        TaskRun run = TaskRun.queueDispatchedStreaming(
                runId, UUID.randomUUID(), deploymentId, 1, "{}",
                executionId, 1, engineId, "commands.streaming"
        );
        DispatcherExecutionResponse response = new DispatcherExecutionResponse(
                executionId, runId, 1, engineId, ExecutionBackendType.LOCAL_DOCKER,
                "STOPPED", 2, null, null, null, endedAt.minusSeconds(10),
                null, endedAt, null, null, null, null, null
        );

        DispatcherExecutionEvent event =
                DispatcherExecutionReconciliationService.toEvent(run, response);

        assertThat(event.messageType()).isEqualTo(ExecutionMessageType.EXECUTION_STOPPED);
        assertThat(event.streamingDeploymentId()).isEqualTo(deploymentId);
        assertThat(event.sequence()).isEqualTo(2);
        assertThat(event.endedAt()).isEqualTo(endedAt);
    }

    @Test
    void recognizesNestedDispatcherNotFoundResponse() {
        HttpClientErrorException notFound = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );

        assertThat(DispatcherExecutionReconciliationService.dispatcherExecutionNotFound(
                new IllegalStateException("wrapped", notFound)
        )).isTrue();
    }

    @Test
    void stopsUntrackedStreamingRunAndDeploymentAfterRecoveryGrace() {
        TaskRunRepository runRepository = mock(TaskRunRepository.class);
        TaskStreamingDeploymentRepository deploymentRepository =
                mock(TaskStreamingDeploymentRepository.class);
        TaskStreamingQueryRepository queryRepository = mock(TaskStreamingQueryRepository.class);
        ComputeEngineExecutionService executionService = mock(ComputeEngineExecutionService.class);
        DispatcherEventApplicationService eventApplicationService =
                mock(DispatcherEventApplicationService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        UUID runEntityId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        UUID engineId = UUID.randomUUID();
        UUID deploymentId = UUID.randomUUID();
        TaskRun run = mock(TaskRun.class);
        when(run.getId()).thenReturn(runEntityId);
        when(run.getTaskType()).thenReturn(TaskType.SPARK_STREAMING_CANVAS);
        when(run.getStatus()).thenReturn(TaskRunStatus.STOP_REQUESTED);
        when(run.getExecutionRunId()).thenReturn(runId);
        when(run.getExternalExecutionId()).thenReturn(executionId);
        when(run.getComputeEngineId()).thenReturn(engineId);
        when(run.getAttempt()).thenReturn(1);
        when(run.getStreamingDeploymentId()).thenReturn(deploymentId);
        when(run.getLastDispatcherEventSequence()).thenReturn(0L);
        when(run.getUpdatedAt()).thenReturn(Instant.now().minusSeconds(5));

        when(runRepository.findAllByTaskTypeAndStatusIn(TaskType.SPARK_CANVAS, List.of(
                TaskRunStatus.QUEUED, TaskRunStatus.RUNNING,
                TaskRunStatus.CANCEL_REQUESTED, TaskRunStatus.STOP_REQUESTED
        ))).thenReturn(List.of());
        when(runRepository.findAllByTaskTypeAndStatusIn(TaskType.SPARK_STREAMING_CANVAS, List.of(
                TaskRunStatus.QUEUED, TaskRunStatus.RUNNING,
                TaskRunStatus.CANCEL_REQUESTED, TaskRunStatus.STOP_REQUESTED
        ))).thenReturn(List.of(run));
        when(runRepository.findByIdForUpdate(runEntityId)).thenReturn(Optional.of(run));

        HttpClientErrorException notFound = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
        when(executionService.execution(engineId, executionId)).thenThrow(notFound);

        TaskStreamingDeployment deployment = mock(TaskStreamingDeployment.class);
        TaskStreamingQuery query = mock(TaskStreamingQuery.class);
        when(deployment.getId()).thenReturn(deploymentId);
        when(deploymentRepository.findByIdForUpdate(deploymentId)).thenReturn(Optional.of(deployment));
        when(queryRepository.findAllByDeploymentIdOrderByOutputNodeNameAsc(deploymentId))
                .thenReturn(List.of(query));

        DispatcherExecutionReconciliationService service =
                new DispatcherExecutionReconciliationService(
                        runRepository,
                        org.mockito.Mockito.mock(cn.superhuang.data.scalpel.business.operations.service.TaskRunAlertService.class),
                        deploymentRepository,
                        queryRepository,
                        executionService,
                        eventApplicationService,
                        new CanvasTaskRunProperties(Duration.ofMinutes(30), Duration.ZERO),
                        transactionManager
                );

        service.reconcile();

        verify(run).stop(
                org.mockito.ArgumentMatchers.contains("未进入 Dispatcher"),
                any(Instant.class)
        );
        verify(runRepository).save(run);
        verify(deployment).markStopped(any(Instant.class));
        verify(deploymentRepository).save(deployment);
        verify(query).markStopped();
        verify(queryRepository).save(query);
        verify(eventApplicationService, never()).accept(any());
    }
}
