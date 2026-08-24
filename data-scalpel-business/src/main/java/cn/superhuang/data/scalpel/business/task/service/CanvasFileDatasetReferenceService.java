package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTable;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableRepository;
import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.business.task.domain.CanvasTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.repository.CanvasTaskDefinitionRepository;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Conservative reference checks for destructive file-dataset lifecycle operations. */
@Service
public class CanvasFileDatasetReferenceService {

    private static final Logger log = LoggerFactory.getLogger(CanvasFileDatasetReferenceService.class);

    private final CanvasTaskDefinitionRepository definitionRepository;
    private final DataTaskRepository taskRepository;
    private final FileDatasetTableRepository tableRepository;
    private final CanvasDefinitionValidator validator;
    private final CanvasDefinitionUpgrader upgrader;
    private final ObjectMapper objectMapper;

    public CanvasFileDatasetReferenceService(
            CanvasTaskDefinitionRepository definitionRepository,
            DataTaskRepository taskRepository,
            FileDatasetTableRepository tableRepository,
            CanvasDefinitionValidator validator,
            CanvasDefinitionUpgrader upgrader,
            ObjectMapper objectMapper
    ) {
        this.definitionRepository = definitionRepository;
        this.taskRepository = taskRepository;
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
        Map<UUID, Set<UUID>> references = referencesByTask();
        boolean referenced = references.values().stream().anyMatch(ids -> ids.stream().anyMatch(targets::contains));
        if (referenced) {
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
        Map<UUID, Set<UUID>> references = referencesByTask();
        Set<UUID> taskIds = references.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(tableIds::contains))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (taskIds.isEmpty()) {
            return;
        }
        boolean protectedReference = taskRepository.findAllById(taskIds).stream()
                .map(DataTask::getStatus)
                .anyMatch(status -> status == TaskStatus.PUBLISHED || status == TaskStatus.DISABLED);
        if (protectedReference) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "已发布或已停用的 Canvas 任务正在引用该文件数据集，不能修改解析参数"
            );
        }
    }

    private Map<UUID, Set<UUID>> referencesByTask() {
        return definitionRepository.findAll().stream().collect(Collectors.toMap(
                CanvasTaskDefinition::getTaskId,
                this::readReferences
        ));
    }

    private Set<UUID> readReferences(CanvasTaskDefinition persisted) {
        try {
            CanvasDefinition definition = objectMapper.readValue(persisted.getDefinitionJson(), CanvasDefinition.class);
            validator.validate(definition);
            CanvasDefinition current = upgrader.upgradeToCurrent(definition);
            Set<UUID> ids = new LinkedHashSet<>();
            for (CanvasNodeDefinition node : current.nodes()) {
                if (node instanceof FileDatasetInputNodeDefinition input) {
                    input.configuration().tables().forEach(selection ->
                            ids.add(UUID.fromString(selection.fileDatasetTableId())));
                }
            }
            return Set.copyOf(ids);
        } catch (RuntimeException exception) {
            log.error(
                    "Cannot inspect Canvas file-dataset references; destructive operation denied taskId={} definitionId={}",
                    persisted.getTaskId(),
                    persisted.getId(),
                    exception
            );
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "存在无法解析的 Canvas 定义，无法安全确认文件数据集引用"
            );
        }
    }
}
