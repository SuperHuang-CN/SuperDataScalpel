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
    void doesNotInventStoppedStateFromDispatcher404() {
        TaskRunRepository runs = mock(TaskRunRepository.class);
        ComputeEngineExecutionService engine = mock(ComputeEngineExecutionService.class);
        DispatcherEventApplicationService events = mock(DispatcherEventApplicationService.class);
        TaskRun run = mock(TaskRun.class);
        UUID engineId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        when(run.getId()).thenReturn(UUID.randomUUID());
        when(run.getComputeEngineId()).thenReturn(engineId);
        when(run.getExternalExecutionId()).thenReturn(executionId);
        when(runs.findDispatchedForReconciliation(any(), org.mockito.ArgumentMatchers.isNull(), any()))
                .thenReturn(List.of(run));
        when(engine.execution(engineId, executionId)).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));
        var service = new DispatcherExecutionReconciliationService(runs,
                mock(cn.superhuang.data.scalpel.business.operations.service.TaskRunAlertService.class),
                engine, events, new CanvasTaskRunProperties(Duration.ofMinutes(30), Duration.ZERO),
                mock(PlatformTransactionManager.class));
        service.reconcile();
        verify(run, never()).stop(any(), any());
        verify(runs, never()).save(any());
        verify(events, never()).accept(any());
    }
}
