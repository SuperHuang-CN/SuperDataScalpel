package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.business.task.repository.*;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateWorkflowTaskDefinitionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowTaskDefinitionServiceTest {
    private final DataTaskRepository tasks = mock(DataTaskRepository.class);
    private final WorkflowTaskDefinitionRepository definitions = mock(WorkflowTaskDefinitionRepository.class);
    private final WorkflowTaskDefinitionService service = new WorkflowTaskDefinitionService(tasks, definitions,
            JsonMapper.builderWithJackson2Defaults().build());
    private final UUID taskId = UUID.randomUUID();

    @Test void permitsIncompleteDraftButRejectsPublishingIt() {
        var task = DataTask.create("工作流", null, TaskType.WORKFLOW, null);
        when(tasks.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));
        var result = service.update(taskId, new UpdateWorkflowTaskDefinitionRequest(WorkflowDefinition.empty()));
        assertEquals(1, result.version());
        assertFalse(service.validateDefinition(result.definition()).valid());
    }
    @Test void acceptsMultipleRootsAndFanInWithoutRequiringUniqueTaskReferences() {
        published(TaskType.LOCAL_SQL);
        var definition = graph(List.of(new WorkflowDefinition.Edge("a", "c"), new WorkflowDefinition.Edge("b", "c")));
        assertTrue(service.validateDefinition(definition).valid());
    }
    @Test void diagnosesCyclesDuplicateEdgesAndMissingEndpoints() {
        published(TaskType.SPARK_CANVAS);
        var result = service.validateDefinition(graph(List.of(new WorkflowDefinition.Edge("a", "b"),
                new WorkflowDefinition.Edge("b", "a"), new WorkflowDefinition.Edge("a", "b"), new WorkflowDefinition.Edge("missing", "c"))));
        assertFalse(result.valid());
        assertTrue(result.problems().stream().anyMatch(p -> p.code().equals("WORKFLOW_CYCLE")));
        assertEquals(2, result.problems().stream().filter(p -> p.code().equals("WORKFLOW_EDGE_INVALID")).count());
    }
    @Test void rejectsNestedAndStreamingTasks() {
        for (var type : List.of(TaskType.WORKFLOW, TaskType.SPARK_STREAMING_CANVAS, TaskType.SPARK_STREAMING_JAR)) {
            published(type);
            assertFalse(service.validateDefinition(graph(List.of())).valid());
        }
    }
    @Test void rejectsDisabledReferences() {
        var task = published(TaskType.SPARK_JAR); task.disable();
        assertFalse(service.validateDefinition(graph(List.of())).valid());
    }
    private DataTask published(TaskType type) {
        var task = DataTask.create("子任务", null, type, null);
        ReflectionTestUtils.setField(task, "id", taskId); task.publish();
        when(tasks.findAllById(any())).thenReturn(List.of(task));
        return task;
    }
    private WorkflowDefinition graph(List<WorkflowDefinition.Edge> edges) {
        return new WorkflowDefinition(1, 4, List.of(new WorkflowDefinition.Node("a", taskId.toString()),
                new WorkflowDefinition.Node("b", taskId.toString()), new WorkflowDefinition.Node("c", taskId.toString())), edges, Map.of());
    }
}
