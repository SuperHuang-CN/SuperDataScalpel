package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.CanvasTaskDefinition;
import cn.superhuang.data.scalpel.business.task.repository.CanvasTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskCanvasFileReferenceRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableRepository;
import cn.superhuang.data.scalpel.contract.task.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CanvasFileReferenceIndexTest {
    @Test
    void incompleteDraftDoesNotBlockUnrelatedFileDeletion() {
        var definitions = mock(CanvasTaskDefinitionRepository.class);
        var references = mock(TaskCanvasFileReferenceRepository.class);
        var upgrader = new CanvasDefinitionUpgrader();
        var validator = new CanvasDefinitionValidator(upgrader);
        var service = new CanvasFileDatasetReferenceService(definitions, references,
                mock(cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository.class),
                mock(FileDatasetTableRepository.class), validator, upgrader, JsonMapper.builder().build());
        var draft = new CanvasDefinition(CanvasDefinition.CURRENT_SCHEMA_VERSION, CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(new FileDatasetInputNodeDefinition(UUID.randomUUID().toString(), "未完成文件输入",
                        new CanvasNodeLayout(0.0, 0.0, 240.0, 120.0), new FileDatasetInputConfiguration("", List.of(new FileDatasetInputTableSelection(""))))), List.of());
        validator.validate(draft);
        var persisted = CanvasTaskDefinition.create(UUID.randomUUID(), draft.schemaVersion(), draft.schemaMinorVersion(), "{}");
        service.replace(persisted, draft);
        assertThat(persisted.fileReferencesCurrent()).isTrue();
        verify(references).saveAll(argThat(items -> !items.iterator().hasNext()));
        service.ensureTablesUnreferenced(List.of(UUID.randomUUID()));
        verify(definitions, never()).findAll();
    }

    @Test
    void unindexedOrDamagedDefinitionsRemainProtectedUntilRepair() {
        var definitions = mock(CanvasTaskDefinitionRepository.class);
        var references = mock(TaskCanvasFileReferenceRepository.class);
        var service = new CanvasFileDatasetReferenceService(definitions, references,
                mock(cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository.class),
                mock(FileDatasetTableRepository.class), mock(CanvasDefinitionValidator.class),
                new CanvasDefinitionUpgrader(), JsonMapper.builder().build());
        when(definitions.existsUnindexedFileReferences()).thenReturn(true);
        assertThatThrownBy(() -> service.ensureTablesUnreferenced(List.of(UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("索引");
        verifyNoInteractions(references);
    }
}
