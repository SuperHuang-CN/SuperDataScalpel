package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarTaskResourceBinding;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarDevelopmentKitJobRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarDevelopmentKitQueueRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskResourceBindingRepository;
import cn.superhuang.data.scalpel.business.task.web.request.CreateSparkJarDevelopmentKitRequest;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceAccessMode;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SparkJarDevelopmentKitServiceTest {
    private final UUID taskId = UUID.randomUUID();
    private final DataTaskRepository tasks = mock(DataTaskRepository.class);
    private final SparkJarTaskDefinitionRepository definitions = mock(SparkJarTaskDefinitionRepository.class);
    private final SparkJarTaskResourceBindingRepository bindings = mock(SparkJarTaskResourceBindingRepository.class);
    private final SparkJarDevelopmentKitJobRepository jobs = mock(SparkJarDevelopmentKitJobRepository.class);
    private final SparkJarDevelopmentKitQueueRepository queue = mock(SparkJarDevelopmentKitQueueRepository.class);
    private final TaskRunArtifactStorage storage = mock(TaskRunArtifactStorage.class);
    private final SparkJarDevelopmentKitService service = new SparkJarDevelopmentKitService(
            tasks, definitions, bindings, jobs, queue, storage, JsonMapper.builderWithJackson2Defaults().build());

    @ParameterizedTest
    @EnumSource(value = TaskType.class, names = {"SPARK_JAR", "SPARK_STREAMING_JAR"})
    void returnsEmptyStateWithoutCreatingDefinitionOrQueryingArtifacts(TaskType type) {
        when(tasks.findById(taskId)).thenReturn(Optional.of(DataTask.create("新任务", null, type, null)));
        when(definitions.findByTaskId(taskId)).thenReturn(Optional.empty());

        var result = service.get(taskId);

        assertEquals(taskId, result.taskId());
        assertEquals(0, result.definitionVersion());
        assertTrue(result.configuration().samples().isEmpty());
        assertTrue(result.configuration().jdbcTables().isEmpty());
        assertNull(result.generation());
        assertNull(result.artifact());
        verify(tasks).findById(taskId);
        verify(definitions).findByTaskId(taskId);
        verifyNoMoreInteractions(tasks, definitions);
        verifyNoInteractions(bindings, jobs, queue, storage);
    }

    @Test
    void preservesSavedDefinitionAndJdbcTableConfiguration() {
        when(tasks.findById(taskId)).thenReturn(Optional.of(DataTask.create("任务", null, TaskType.SPARK_JAR, null)));
        var definition = SparkJarTaskDefinition.create(taskId);
        definition.saveDevelopmentKitConfig("""
                {"samples":[],"jdbcTables":[{"bindingName":"source","catalog":null,
                "schema":"public","table":"assets","mode":"ROW_COUNT","rowCount":10,"percentage":null}]}
                """);
        when(definitions.findByTaskId(taskId)).thenReturn(Optional.of(definition));
        when(bindings.findAllByTaskIdOrderByCreatedAtAsc(taskId)).thenReturn(List.of(
                SparkJarTaskResourceBinding.create(taskId, "source", SparkJarResourceType.JDBC_DATA_SOURCE,
                        UUID.randomUUID(), SparkJarResourceAccessMode.READ)));

        var result = service.get(taskId);

        assertEquals(definition.getVersion(), result.definitionVersion());
        assertTrue(result.configuration().samples().isEmpty());
        assertEquals(1, result.configuration().jdbcTables().size());
        assertEquals("assets", result.configuration().jdbcTables().getFirst().table());
        assertEquals(10, result.configuration().jdbcTables().getFirst().rowCount());
        assertNull(result.artifact());
        verify(definitions).findByTaskId(taskId);
        verifyNoMoreInteractions(definitions);
    }

    @Test
    void generationStillRequiresSavedDefinition() {
        when(tasks.findByIdForUpdate(taskId)).thenReturn(Optional.of(
                DataTask.create("任务", null, TaskType.SPARK_JAR, null)));
        var failure = assertThrows(ResponseStatusException.class, () -> service.generate(taskId,
                new CreateSparkJarDevelopmentKitRequest(1, List.of(), List.of())));
        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verifyNoInteractions(jobs, queue, storage, bindings);
    }

    @Test
    void missingTaskStillReturnsNotFound() {
        var failure = assertThrows(ResponseStatusException.class, () -> service.get(taskId));
        assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
        verifyNoInteractions(definitions, bindings, jobs, queue, storage);
    }

    @Test
    void rejectsNonJarTaskInsteadOfReturningEmptyState() {
        when(tasks.findById(taskId)).thenReturn(Optional.of(
                DataTask.create("SQL", null, TaskType.LOCAL_SQL, null)));
        var failure = assertThrows(ResponseStatusException.class, () -> service.get(taskId));
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        verifyNoInteractions(definitions, bindings, jobs, queue, storage);
    }
}
