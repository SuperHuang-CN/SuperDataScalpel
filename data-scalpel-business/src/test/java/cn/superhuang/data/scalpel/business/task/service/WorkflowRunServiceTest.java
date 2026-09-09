package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.business.task.repository.*;
import cn.superhuang.data.scalpel.business.task.execution.service.TaskExecutionOutboxService;
import cn.superhuang.data.scalpel.business.operations.service.TaskRunAlertService;
import cn.superhuang.data.scalpel.contract.execution.*;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowRunServiceTest {
    private final TaskRunRepository runs = mock(TaskRunRepository.class);
    private final DataTaskRepository tasks = mock(DataTaskRepository.class);
    private final WorkflowTaskDefinitionRepository definitions = mock(WorkflowTaskDefinitionRepository.class);
    private final TaskExecutionOutboxService outbox = mock(TaskExecutionOutboxService.class);
    private final TaskRunWorker worker = mock(TaskRunWorker.class);
    private final TaskRunAlertService alerts = mock(TaskRunAlertService.class);
    private final JsonMapper mapper = JsonMapper.builderWithJackson2Defaults().build();
    private final WorkflowRunService service = new WorkflowRunService(runs, tasks, definitions,
            new WorkflowTaskDefinitionService(tasks, definitions, mapper), outbox, worker, alerts);
    private final UUID parentId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();
    private final List<TaskRun> children = new ArrayList<>();

    @Test void submitsRootsInDefinitionOrderAndWaitsForAllPredecessors() {
        var parent = parent(4);
        assertEquals(List.of("a", "b"), service.advance(parentId).stream().map(WorkflowDefinition.Node::id).toList());
        assertEquals(TaskRunStatus.RUNNING, parent.getStatus());
        var a = child("a"); a.start(); a.succeed(1);
        var b = child("b"); b.start();
        assertTrue(service.advance(parentId).isEmpty());
        b.succeed(2);
        assertEquals(List.of("c"), service.advance(parentId).stream().map(WorkflowDefinition.Node::id).toList());
    }
    @Test void queuedChildrenConsumeParallelSlotsAndDuplicateNodesAreRejected() {
        parent(1);
        assertEquals(List.of("a"), service.advance(parentId).stream().map(WorkflowDefinition.Node::id).toList());
        child("a");
        assertTrue(service.advance(parentId).isEmpty());
        assertThrows(ResponseStatusException.class, () -> service.lockForSubmission(parentId, "a", taskId));
    }
    @Test void qualityFailureConclusionDoesNotBlockExecutionSuccess() {
        var parent = parent(4); parent.start();
        for (String node : List.of("a", "b", "c")) { var child = child(node); child.start(); child.succeed(4); }
        ReflectionTestUtils.setField(children.getFirst(), "qualityConclusion", QualityConclusion.FAILED);
        service.advance(parentId);
        assertEquals(TaskRunStatus.SUCCESS, parent.getStatus());
        assertNull(parent.getAffectedRows());
        verify(alerts).capture(parent);
    }
    @Test void failureStopsOnlyThisParentsQueuedChildrenAndLocatesTheFailedNode() {
        var parent = parent(4); parent.start();
        var a = child("a"); a.fail("失败", "ERROR");
        var b = child("b");
        service.advance(parentId);
        assertEquals(TaskRunStatus.FAILED, parent.getStatus());
        assertEquals("a", parent.getErrorNodeId());
        assertEquals(TaskRunStatus.CANCELLED, b.getStatus());
        assertTrue(service.advance(parentId).isEmpty());
        verifyNoInteractions(outbox);
    }
    @Test void cancellationWaitsForJdbcExitAndPreventsSubmissionAfterPreparation() {
        var parent = parent(4); parent.start();
        var a = child("a"); a.start();
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.cancel(parentId);
            verifyNoInteractions(worker);
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
        verify(worker).cancel(a.getId());
        assertEquals(TaskRunStatus.CANCEL_REQUESTED, parent.getStatus());
        assertThrows(ResponseStatusException.class, () -> service.lockForSubmission(parentId, "b", taskId));
        service.advance(parentId);
        assertEquals(TaskRunStatus.CANCEL_REQUESTED, parent.getStatus());
        a.cancel("已退出", a.getStartedAt(), Instant.now());
        service.advance(parentId);
        assertEquals(TaskRunStatus.CANCELLED, parent.getStatus());
    }
    @Test void unconfirmedTerminationIsFailureInsteadOfSuccessfulCancellation() {
        var parent = parent(4); parent.requestCancel();
        child("a").fail(new SafeExecutionError("EXECUTION_TERMINATION_UNCONFIRMED", "无法确认终止"));
        service.advance(parentId);
        assertEquals(TaskRunStatus.FAILED, parent.getStatus());
        assertEquals("WORKFLOW_STOP_FAILED", parent.getErrorCode());
    }
    @Test void preparationFailureIsVisibleWithoutCreatingAChildRun() {
        var parent = parent(4); parent.start();
        service.submissionFailed(parentId, "a", new IllegalStateException("private connection detail"));
        assertEquals(TaskRunStatus.FAILED, parent.getStatus());
        assertFalse(parent.getMessage().contains("private"));
        var projection = service.get(parentId);
        assertEquals("FAILED", projection.nodes().getFirst().status());
        assertNull(projection.nodes().getFirst().childRun());
        assertEquals("BLOCKED", projection.nodes().get(1).status());
    }
    @Test void restartFailsParentAndQueuesHardStopForExactExternalExecution() {
        var parent = parent(4);
        var executionId = UUID.randomUUID(); var internalRunId = UUID.randomUUID(); var engineId = UUID.randomUUID();
        var spark = TaskRun.queueDispatchedCanvas(internalRunId, taskId, 2, "{}", executionId, 1,
                Instant.now().plusSeconds(60), engineId, "engine-command");
        spark.attachWorkflow(parentId, "a"); add(spark);
        when(runs.findAllByTaskTypeAndStatusIn(TaskType.WORKFLOW, WorkflowRunService.ACTIVE)).thenReturn(List.of(parent));
        service.recoverInterrupted();
        assertEquals(TaskRunStatus.FAILED, parent.getStatus());
        assertEquals("APPLICATION_RESTARTED", parent.getErrorCode());
        assertEquals(TaskRunStatus.CANCEL_REQUESTED, spark.getStatus());
        verify(outbox).enqueue(eq("engine-command"), argThat(command -> command instanceof ForceTerminateExecutionCommand stop
                && stop.executionId().equals(executionId) && stop.runId().equals(internalRunId)));
    }
    private TaskRun parent(int parallelism) {
        var definition = new WorkflowDefinition(1, parallelism, List.of(new WorkflowDefinition.Node("a", taskId.toString()),
                new WorkflowDefinition.Node("b", taskId.toString()), new WorkflowDefinition.Node("c", taskId.toString())),
                List.of(new WorkflowDefinition.Edge("a", "c"), new WorkflowDefinition.Edge("b", "c")), Map.of());
        var parent = TaskRun.queueWorkflow(UUID.randomUUID(), 1, mapper.writeValueAsString(definition), null, null, false);
        ReflectionTestUtils.setField(parent, "id", parentId);
        when(runs.findByIdForUpdate(parentId)).thenReturn(Optional.of(parent));
        when(runs.findById(parentId)).thenReturn(Optional.of(parent));
        when(runs.findAllByParentRunIdOrderByQueuedAtAsc(parentId)).thenAnswer(invocation -> List.copyOf(children));
        when(tasks.findAllById(any())).thenReturn(List.of());
        return parent;
    }
    private TaskRun child(String nodeId) {
        var run = TaskRun.queue(taskId, 1, "{}"); run.attachWorkflow(parentId, nodeId); add(run); return run;
    }
    private void add(TaskRun run) {
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID()); children.add(run);
        when(runs.findByIdForUpdate(run.getId())).thenReturn(Optional.of(run));
    }
}
