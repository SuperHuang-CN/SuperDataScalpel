package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.business.task.repository.*;
import cn.superhuang.data.scalpel.business.task.web.request.CreateSparkJarDevelopmentKitRequest.JdbcTableSample;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateSparkJarTaskDefinitionRequest.DevelopmentConfiguration;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceAccessMode;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SparkJarDevelopmentConfigurationSaveTest {
    private final UUID taskId = UUID.randomUUID();
    private final SparkJarTaskResourceBindingRepository bindings = mock(SparkJarTaskResourceBindingRepository.class);
    private final SparkJarDevelopmentKitJobRepository jobs = mock(SparkJarDevelopmentKitJobRepository.class);
    private final SparkJarDevelopmentKitQueueRepository queue = mock(SparkJarDevelopmentKitQueueRepository.class);
    private final TaskRunArtifactStorage storage = mock(TaskRunArtifactStorage.class);
    private final SparkJarDevelopmentKitService service = new SparkJarDevelopmentKitService(
            mock(DataTaskRepository.class), mock(SparkJarTaskDefinitionRepository.class), bindings, jobs, queue,
            storage, JsonMapper.builderWithJackson2Defaults().build());

    @Test
    void savesSelectionWithoutReadingDataGeneratingPackageOrChangingRuntimeVersion() {
        var definition = SparkJarTaskDefinition.create(taskId);
        UUID artifact = UUID.randomUUID();
        definition.replaceCurrentDevelopmentKit(artifact);
        int version = definition.getVersion();
        when(bindings.findAllByTaskIdOrderByCreatedAtAsc(taskId)).thenReturn(List.of(
                SparkJarTaskResourceBinding.create(taskId, "input", SparkJarResourceType.JDBC_DATA_SOURCE,
                        UUID.randomUUID(), SparkJarResourceAccessMode.READ)));
        service.saveConfiguration(definition, configuration("input"));
        assertTrue(definition.getDevelopmentKitConfigJson().contains("assets"));
        assertEquals(version, definition.getVersion());
        assertEquals(artifact, definition.getCurrentDevelopmentKitJobId());
        verifyNoInteractions(jobs, queue, storage);
    }

    @Test
    void rejectsSamplesNotBackedByReadableBindingAndPreservesOldConfiguration() {
        var definition = SparkJarTaskDefinition.create(taskId);
        definition.saveDevelopmentKitConfig("previous");
        when(bindings.findAllByTaskIdOrderByCreatedAtAsc(taskId)).thenReturn(List.of(
                SparkJarTaskResourceBinding.create(taskId, "output", SparkJarResourceType.JDBC_DATA_SOURCE,
                        UUID.randomUUID(), SparkJarResourceAccessMode.WRITE)));
        assertThrows(ResponseStatusException.class, () -> service.saveConfiguration(definition, configuration("output")));
        assertEquals("previous", definition.getDevelopmentKitConfigJson());
        verifyNoInteractions(jobs, queue, storage);
    }

    private static DevelopmentConfiguration configuration(String name) {
        return new DevelopmentConfiguration(List.of(), List.of(new JdbcTableSample(
                name, null, "public", "assets", SparkJarDevelopmentKitSampleMode.ROW_COUNT, 20, null)));
    }
}
