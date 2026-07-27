package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.canvas.CanvasDefinition;
import cn.superhuang.data.scalpel.business.task.domain.CanvasTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.domain.TaskCanvasModelReference;
import cn.superhuang.data.scalpel.business.task.domain.TaskCanvasModelReferenceRole;
import cn.superhuang.data.scalpel.business.task.repository.CanvasTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskCanvasModelReferenceRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskStreamingDeploymentRepository;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateCanvasTaskDefinitionRequest;
import cn.superhuang.data.scalpel.business.task.web.response.CanvasTaskDefinitionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CanvasTaskDefinitionService {

    static final int MAX_DEFINITION_BYTES = 5 * 1024 * 1024;

    private final DataTaskRepository taskRepository;
    private final CanvasTaskDefinitionRepository definitionRepository;
    private final TaskCanvasModelReferenceRepository modelReferenceRepository;
    private final TaskStreamingDeploymentRepository streamingDeploymentRepository;
    private final CanvasDefinitionValidator validator;
    private final CanvasDefinitionUpgrader upgrader;
    private final ObjectMapper objectMapper;

    public CanvasTaskDefinitionService(
            DataTaskRepository taskRepository,
            CanvasTaskDefinitionRepository definitionRepository,
            TaskCanvasModelReferenceRepository modelReferenceRepository,
            TaskStreamingDeploymentRepository streamingDeploymentRepository,
            CanvasDefinitionValidator validator,
            CanvasDefinitionUpgrader upgrader,
            ObjectMapper objectMapper
    ) {
        this.taskRepository = taskRepository;
        this.definitionRepository = definitionRepository;
        this.modelReferenceRepository = modelReferenceRepository;
        this.streamingDeploymentRepository = streamingDeploymentRepository;
        this.validator = validator;
        this.upgrader = upgrader;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CanvasTaskDefinitionResponse get(UUID taskId) {
        requireCanvasTask(taskId);
        return definitionRepository.findByTaskId(taskId)
                .map(this::response)
                .orElseGet(() -> CanvasTaskDefinitionResponse.unconfigured(taskId));
    }

    @Transactional
    public CanvasTaskDefinitionResponse update(UUID taskId, UpdateCanvasTaskDefinitionRequest request) {
        DataTask task = requireCanvasTask(taskId);
        if (task.getStatus() != TaskStatus.DRAFT && task.getStatus() != TaskStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布任务不能修改定义，请先停用");
        }
        if (task.getType() == TaskType.SPARK_STREAMING_CANVAS
                && streamingDeploymentRepository.existsByTaskIdAndActualStateIn(
                taskId,
                List.of(
                        cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentActualState.STARTING,
                        cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentActualState.RUNNING,
                        cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentActualState.STOPPING))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "活动实时任务必须先停止，再修改定义");
        }
        CanvasDefinition sourceDefinition = request.definition();
        validator.validate(sourceDefinition);
        CanvasDefinition definition = upgrader.upgradeToCurrent(sourceDefinition);
        String serialized = serialize(definition);
        if (serialized.getBytes(StandardCharsets.UTF_8).length > MAX_DEFINITION_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Canvas 定义不能超过 5 MiB");
        }
        replaceModelReferences(taskId, definition);

        CanvasTaskDefinition persisted = definitionRepository.findByTaskId(taskId).orElse(null);
        if (persisted != null && persisted.hasSameContent(
                definition.schemaVersion(), definition.schemaMinorVersion(), serialized)) {
            return response(persisted);
        }
        if (persisted == null) {
            persisted = CanvasTaskDefinition.create(
                    taskId, definition.schemaVersion(), definition.schemaMinorVersion(), serialized);
        } else {
            persisted.update(definition.schemaVersion(), definition.schemaMinorVersion(), serialized);
        }
        return response(definitionRepository.saveAndFlush(persisted));
    }

    private void replaceModelReferences(UUID taskId, CanvasDefinition definition) {
        List<TaskCanvasModelReference> references = new ArrayList<>();
        for (CanvasDefinition.CanvasNodeDefinition node : definition.nodes()) {
            String modelId = null;
            TaskCanvasModelReferenceRole role = null;
            if (node instanceof CanvasDefinition.ModelInputNodeDefinition input) {
                modelId = input.configuration().modelId();
                role = TaskCanvasModelReferenceRole.INPUT;
            } else if (node instanceof CanvasDefinition.ModelOutputNodeDefinition output) {
                modelId = output.configuration().targetModelId();
                role = TaskCanvasModelReferenceRole.OUTPUT;
            }
            if (modelId == null || modelId.isBlank()) continue;
            references.add(TaskCanvasModelReference.create(
                    taskId,
                    UUID.fromString(node.id()),
                    UUID.fromString(modelId),
                    role
            ));
        }
        modelReferenceRepository.deleteAllByTaskId(taskId);
        modelReferenceRepository.saveAll(references);
    }

    private CanvasTaskDefinitionResponse response(CanvasTaskDefinition definition) {
        return new CanvasTaskDefinitionResponse(
                definition.getTaskId(), true, definition.getVersion(), deserialize(definition.getDefinitionJson()),
                definition.getUpdatedAt()
        );
    }

    private DataTask requireCanvasTask(UUID taskId) {
        DataTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!task.getType().isCanvas()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不是 Spark Canvas 任务");
        }
        return task;
    }

    private String serialize(CanvasDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法保存 Canvas 任务定义", exception);
        }
    }

    CanvasDefinition deserialize(String content) {
        try {
            CanvasDefinition definition = objectMapper.readValue(content, CanvasDefinition.class);
            validator.validate(definition);
            return upgrader.upgradeToCurrent(definition);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法读取 Canvas 任务定义", exception);
        }
    }
}
