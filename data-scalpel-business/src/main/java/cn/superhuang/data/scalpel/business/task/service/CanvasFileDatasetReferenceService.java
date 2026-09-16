package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTable;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableRepository;
import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.business.task.domain.CanvasTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.TaskCanvasFileReference;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.repository.CanvasTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskCanvasFileReferenceRepository;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Conservative reference checks for destructive file-dataset lifecycle operations. */
@Service
public class CanvasFileDatasetReferenceService {

    private static final Logger log = LoggerFactory.getLogger(CanvasFileDatasetReferenceService.class);

    private final CanvasTaskDefinitionRepository definitionRepository;
    private final TaskCanvasFileReferenceRepository references;
    private final DataTaskRepository tasks;
    private final FileDatasetTableRepository tableRepository;
    private final CanvasDefinitionValidator validator;
    private final CanvasDefinitionUpgrader upgrader;
    private final ObjectMapper objectMapper;

    public CanvasFileDatasetReferenceService(
            CanvasTaskDefinitionRepository definitionRepository,
            TaskCanvasFileReferenceRepository references,
            DataTaskRepository tasks,
            FileDatasetTableRepository tableRepository,
            CanvasDefinitionValidator validator,
            CanvasDefinitionUpgrader upgrader,
            ObjectMapper objectMapper
    ) {
        this.definitionRepository = definitionRepository;
        this.references = references;
        this.tasks = tasks;
        this.tableRepository = tableRepository;
        this.validator = validator;
        this.upgrader = upgrader;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public void ensureTablesUnreferenced(Collection<UUID> tableIds) {
        Set<UUID> targets = tableIds == null ? Set.of() : Set.copyOf(tableIds);
        if (targets.isEmpty()) {
            return;
        }
        requireCompleteIndex();
        if (references.existsByFileTableIdIn(targets)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "文件数据集表仍被 Canvas 任务引用，不能删除或替换"
            );
        }
    }

    @Transactional(readOnly = true)
    public void ensureParsingOptionsMutable(UUID fileDatasetId) {
        Set<UUID> tableIds = tableRepository
                .findByFileDatasetIdOrderByCreatedAtAsc(fileDatasetId)
                .stream()
                .map(FileDatasetTable::getId)
                .collect(Collectors.toSet());
        if (tableIds.isEmpty()) {
            return;
        }
        requireCompleteIndex();
        boolean protectedReference = references.existsProtectedReference(
                tableIds, List.of(TaskStatus.PUBLISHED, TaskStatus.DISABLED));
        if (protectedReference) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "已发布或已停用的 Canvas 任务正在引用该文件数据集，不能修改解析参数"
            );
        }
    }

    private void requireCompleteIndex() {
        if (definitionRepository.existsUnindexedFileReferences()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Canvas 文件引用索引尚未完成或定义损坏，请修复并重新保存对应定义后重试");
        }
    }

    /** Called in the same transaction as the Canvas definition write. */
    @Transactional
    public void replace(CanvasTaskDefinition persisted, CanvasDefinition definition) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (CanvasNodeDefinition node : definition.nodes()) {
            if (node instanceof FileDatasetInputNodeDefinition input) {
                input.configuration().tables().stream()
                        .map(selection -> selection.fileDatasetTableId())
                        .filter(id -> !id.isBlank()).map(UUID::fromString).forEach(ids::add);
            }
        }
        references.deleteAllByTaskId(persisted.getTaskId());
        references.flush();
        references.saveAll(ids.stream().map(id -> TaskCanvasFileReference.create(persisted.getTaskId(), id)).toList());
        persisted.fileReferencesIndexed();
    }

    @Transactional
    public void deleteForTask(UUID taskId) {
        references.deleteAllByTaskId(taskId);
    }

    /** Each legacy definition is rebuilt in its own short transaction under the same task lock as writes. */
    @Transactional
    public void rebuild(UUID taskId) {
        if (tasks.findByIdForUpdate(taskId).isEmpty()) return;
        CanvasTaskDefinition persisted = definitionRepository.findByTaskIdForUpdate(taskId).orElse(null);
        if (persisted == null || persisted.fileReferencesCurrent()) return;
        CanvasDefinition definition;
        try {
            definition = objectMapper.readValue(persisted.getDefinitionJson(), CanvasDefinition.class);
            validator.validate(definition);
            definition = upgrader.upgradeToCurrent(definition);
        } catch (RuntimeException exception) {
            persisted.fileReferenceIndexFailed();
            log.error("Canvas 文件引用索引需要修复 taskId={}", taskId, exception);
            return;
        }
        replace(persisted, definition);
    }
}
