package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunLogResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskRunLogWaitingTest {
    private final UUID id = UUID.randomUUID();
    private final UUID engine = UUID.randomUUID();
    private final UUID execution = UUID.randomUUID();
    private final TaskRun run = mock(TaskRun.class);
    private final ComputeEngineExecutionService compute = mock(ComputeEngineExecutionService.class);
    private TaskRunArtifactQueryService service;

    @BeforeEach void setup() {
        var repository = mock(TaskRunRepository.class);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(repository.findById(id)).thenReturn(Optional.of(run));
        when(run.getId()).thenReturn(id);
        when(run.getTaskType()).thenReturn(TaskType.SPARK_JAR);
        when(run.getComputeEngineId()).thenReturn(engine);
        when(run.getExternalExecutionId()).thenReturn(execution);
        when(run.getExecutionRunId()).thenReturn(id);
        when(run.getAttempt()).thenReturn(1);
        ObjectProvider<TaskRunArtifactStorage> storage = new org.springframework.beans.factory.support.StaticListableBeanFactory()
                .getBeanProvider(TaskRunArtifactStorage.class);
        service = new TaskRunArtifactQueryService(repository, compute, storage, new ObjectMapper(), transactions);
    }

    @Test void queuedMissingExecutionIsWaitingWithoutResubmission() {
        when(run.getStatus()).thenReturn(TaskRunStatus.QUEUED);
        when(compute.executionLog(engine, execution, 1)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        var result = service.logs(id);
        assertThat(result.status()).isEqualTo(TaskRunLogResponse.Status.WAITING);
        assertThat(result.source()).isEqualTo(TaskRunLogResponse.Source.NONE);
        assertThat(result.message()).contains("等待 Dispatcher 接收执行命令");
        verify(compute).executionLog(engine, execution, 1);
        verifyNoMoreInteractions(compute);
    }

    @Test void runningMissingExecutionRemainsAnError() {
        when(run.getStatus()).thenReturn(TaskRunStatus.RUNNING);
        when(compute.executionLog(engine, execution, 1)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.logs(id)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test void terminalMissingExecutionRemainsAnError() {
        when(run.getStatus()).thenReturn(TaskRunStatus.FAILED);
        when(compute.executionLog(engine, execution, 1)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.logs(id)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test void queuedConnectivityFailureIsNotHidden() {
        when(run.getStatus()).thenReturn(TaskRunStatus.QUEUED);
        var failure = new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Dispatcher 当前无法提供运行日志");
        when(compute.executionLog(engine, execution, 1)).thenThrow(failure);
        assertThatThrownBy(() -> service.logs(id)).isSameAs(failure);
    }
}
