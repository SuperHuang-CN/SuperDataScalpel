package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.contract.task.*;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CanvasTaskDefinitionService {

    static final int MAX_DEFINITION_BYTES = 5 * 1024 * 1024;

    private final DataTaskRepository taskRepository;
    private final CanvasTaskDefinitionRepository definitionRepository;
    private final TaskCanvasModelReferenceRepository modelReferenceRepository;
    private final TaskDataSourceReferenceIndexService dataSourceReferenceIndexService;
    private final TaskStreamingDeploymentRepository streamingDeploymentRepository;
    private final cn.superhuang.data.scalpel.business.task.repository.TaskStreamingConfigurationRepository
            streamingConfigurationRepository;
    private final CanvasFileDatasetReferenceService fileReferences;
    private final CanvasDefinitionValidator validator;
    private final CanvasDefinitionUpgrader upgrader;
    private final TmqConsumerGroupCleanupService tmqCleanupService;
    private final ObjectMapper objectMapper;

    public CanvasTaskDefinitionService(
            DataTaskRepository taskRepository,
            CanvasTaskDefinitionRepository definitionRepository,
            TaskCanvasModelReferenceRepository modelReferenceRepository,
            TaskDataSourceReferenceIndexService dataSourceReferenceIndexService,
            TaskStreamingDeploymentRepository streamingDeploymentRepository,
            cn.superhuang.data.scalpel.business.task.repository.TaskStreamingConfigurationRepository
                    streamingConfigurationRepository,
            CanvasFileDatasetReferenceService fileReferences,
            CanvasDefinitionValidator validator,
            CanvasDefinitionUpgrader upgrader,
            TmqConsumerGroupCleanupService tmqCleanupService,
            ObjectMapper objectMapper
    ) {
        this.taskRepository = taskRepository;
        this.definitionRepository = definitionRepository;
        this.modelReferenceRepository = modelReferenceRepository;
        this.dataSourceReferenceIndexService = dataSourceReferenceIndexService;
        this.streamingDeploymentRepository = streamingDeploymentRepository;
        this.streamingConfigurationRepository = streamingConfigurationRepository;
        this.fileReferences = fileReferences;
        this.validator = validator;
        this.upgrader = upgrader;
        this.tmqCleanupService = tmqCleanupService;
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
        taskRepository.findByIdForUpdate(taskId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
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
        CanvasDefinition definition = upgrader.upgradeToCurrent(
                sourceDefinition, legacyTriggerInterval(taskId));
        validator.validate(definition);
        String serialized = serialize(definition);
        if (serialized.getBytes(StandardCharsets.UTF_8).length > MAX_DEFINITION_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Canvas 定义不能超过 5 MiB");
        }
        replaceModelReferences(taskId, definition);

        CanvasTaskDefinition persisted = definitionRepository.findByTaskIdForUpdate(taskId).orElse(null);
        if (persisted != null && persisted.hasSameContent(
                definition.schemaVersion(), definition.schemaMinorVersion(), serialized)) {
            fileReferences.replace(persisted, definition);
            dataSourceReferenceIndexService.replaceCanvasReferences(taskId, persisted.getVersion(), definition);
            return response(persisted);
        }
        CanvasDefinition previousDefinition = persisted == null ? null : requireReadable(persisted);
        tmqCleanupService.enqueueRemovedGroups(taskId, previousDefinition, definition);
        if (persisted == null) {
            persisted = CanvasTaskDefinition.create(
                    taskId, definition.schemaVersion(), definition.schemaMinorVersion(), serialized);
        } else {
            persisted.update(definition.schemaVersion(), definition.schemaMinorVersion(), serialized);
        }
        CanvasTaskDefinition saved = definitionRepository.saveAndFlush(persisted);
        fileReferences.replace(saved, definition);
        dataSourceReferenceIndexService.replaceCanvasReferences(taskId, saved.getVersion(), definition);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public CanvasDefinition validateTrialDraft(UUID taskId, CanvasDefinition sourceDefinition) {
        requireCanvasTask(taskId);
        CanvasDefinition definition = upgrader.upgradeToCurrent(
                sourceDefinition, legacyTriggerInterval(taskId));
        validator.validate(definition);
        String serialized = serialize(definition);
        if (serialized.getBytes(StandardCharsets.UTF_8).length > MAX_DEFINITION_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Canvas 定义不能超过 5 MiB");
        }
        return definition;
    }

    @Transactional
    public CanvasTaskDefinitionResponse updateUnboundedInputTriggerInterval(
            UUID taskId,
            int triggerIntervalSeconds
    ) {
        if (triggerIntervalSeconds < 1 || triggerIntervalSeconds > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微批间隔必须在 1 到 300 秒之间");
        }
        CanvasTaskDefinition persisted = definitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "实时 Canvas 定义不存在"));
        CanvasDefinition definition = requireReadable(persisted);
        List<CanvasNodeDefinition> unboundedInputs = definition.nodes().stream()
                .filter(CanvasTaskDefinitionService::isUnboundedInput)
                .toList();
        if (unboundedInputs.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "实时任务必须且只能配置一个无界输入节点");
        }
        String targetId = unboundedInputs.getFirst().id();
        List<CanvasNodeDefinition> nodes = definition.nodes().stream()
                .map(node -> node.id().equals(targetId)
                        ? withTriggerInterval(node, triggerIntervalSeconds) : node)
                .toList();
        return update(taskId, new UpdateCanvasTaskDefinitionRequest(new CanvasDefinition(
                definition.schemaVersion(), definition.schemaMinorVersion(), nodes, definition.edges())));
    }

    public int unboundedInputTriggerInterval(CanvasDefinition definition) {
        List<CanvasNodeDefinition> inputs = definition.nodes().stream()
                .filter(CanvasTaskDefinitionService::isUnboundedInput)
                .toList();
        if (inputs.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "实时任务必须且只能配置一个无界输入节点");
        }
        Integer value = switch (inputs.getFirst()) {
            case KafkaInputNodeDefinition input -> input.configuration().triggerIntervalSeconds();
            case TdEngineTmqInputNodeDefinition input -> input.configuration().triggerIntervalSeconds();
            case JdbcIncrementalInputNodeDefinition input -> input.configuration().triggerIntervalSeconds();
            default -> null;
        };
        if (value == null || value < 1 || value > 300) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "无界输入节点微批间隔无效");
        }
        return value;
    }

    private static boolean isUnboundedInput(CanvasNodeDefinition node) {
        return node instanceof KafkaInputNodeDefinition
                || node instanceof TdEngineTmqInputNodeDefinition
                || node instanceof JdbcIncrementalInputNodeDefinition;
    }

    private static CanvasNodeDefinition withTriggerInterval(
            CanvasNodeDefinition node,
            int triggerIntervalSeconds
    ) {
        return switch (node) {
            case KafkaInputNodeDefinition input -> {
                KafkaInputConfiguration value = input.configuration();
                yield new KafkaInputNodeDefinition(input.id(), input.name(), input.layout(),
                        new KafkaInputConfiguration(
                                value.dataSourceId(), value.topic(), value.valueSchema(),
                                value.outputTableName(), value.startingOffsets(), triggerIntervalSeconds,
                                value.valueFormat(), value.metadataFields()));
            }
            case TdEngineTmqInputNodeDefinition input -> {
                TdEngineTmqInputConfiguration value = input.configuration();
                yield new TdEngineTmqInputNodeDefinition(input.id(), input.name(), input.layout(),
                        new TdEngineTmqInputConfiguration(
                                value.dataSourceId(), value.topicName(), value.catalogName(),
                                value.supertableName(), value.topicDefinitionFingerprint(),
                                value.outputTableName(), value.startingOffsets(),
                                value.maxOffsetsPerVGroupPerTrigger(), triggerIntervalSeconds,
                                value.eventTimeColumn(), value.watermarkDelaySeconds()));
            }
            case JdbcIncrementalInputNodeDefinition input -> {
                JdbcIncrementalInputConfiguration value = input.configuration();
                yield new JdbcIncrementalInputNodeDefinition(input.id(), input.name(), input.layout(),
                        new JdbcIncrementalInputConfiguration(
                                value.dataSourceId(), value.tableName(), value.outputTableName(),
                                value.incrementalTimeColumn(), value.startPosition(), value.startTime(),
                                value.cursorTimeZone(), value.visibilityDelaySeconds(), triggerIntervalSeconds));
            }
            default -> throw new ResponseStatusException(HttpStatus.CONFLICT, "节点不是无界输入");
        };
    }

    private void replaceModelReferences(UUID taskId, CanvasDefinition definition) {
        List<TaskCanvasModelReference> references = new ArrayList<>();
        Set<ModelReferenceKey> referenceKeys = new LinkedHashSet<>();
        for (CanvasNodeDefinition node : definition.nodes()) {
            if (node instanceof ModelInputNodeDefinition input) {
                input.configuration().models().forEach(selection -> addModelReference(
                        references, referenceKeys, taskId, node, selection.modelId(), TaskCanvasModelReferenceRole.INPUT));
            } else if (node instanceof ModelOutputNodeDefinition output) {
                output.configuration().writes().forEach(write -> addModelReference(
                        references, referenceKeys, taskId, node, write.targetModelId(), TaskCanvasModelReferenceRole.OUTPUT));
            } else if (node instanceof ModelSnapshotSyncOutputNodeDefinition output) {
                addModelReference(references, referenceKeys, taskId, node, output.configuration().targetModelId(),
                        TaskCanvasModelReferenceRole.OUTPUT);
            }
        }
        modelReferenceRepository.deleteAllByTaskId(taskId);
        modelReferenceRepository.saveAll(references);
    }

    private static void addModelReference(
            List<TaskCanvasModelReference> references,
            Set<ModelReferenceKey> referenceKeys,
            UUID taskId,
            CanvasNodeDefinition node,
            String modelId,
            TaskCanvasModelReferenceRole role
    ) {
        if (modelId == null || modelId.isBlank()) return;
        UUID nodeId = UUID.fromString(node.id());
        UUID parsedModelId = UUID.fromString(modelId);
        if (!referenceKeys.add(new ModelReferenceKey(nodeId, parsedModelId, role))) return;
        references.add(TaskCanvasModelReference.create(
                taskId, nodeId, parsedModelId, role));
    }

    private record ModelReferenceKey(UUID nodeId, UUID modelId, TaskCanvasModelReferenceRole role) {
    }

    private CanvasTaskDefinitionResponse response(CanvasTaskDefinition definition) {
        if (!isReadable(definition)) {
            return CanvasTaskDefinitionResponse.incompatible(
                    definition.getTaskId(),
                    definition.getVersion(),
                    definition.getSchemaVersion(),
                    definition.getSchemaMinorVersion(),
                    incompatibleMessage(definition),
                    definition.getUpdatedAt()
            );
        }
        return CanvasTaskDefinitionResponse.loaded(
                definition.getTaskId(),
                definition.getVersion(),
                definition.getSchemaVersion(),
                definition.getSchemaMinorVersion(),
                deserialize(definition.getDefinitionJson(), legacyTriggerInterval(definition.getTaskId())),
                definition.getUpdatedAt()
        );
    }

    public boolean isReadable(CanvasTaskDefinition definition) {
        return definition != null && upgrader.supports(
                definition.getSchemaVersion(), definition.getSchemaMinorVersion());
    }

    public Optional<CanvasDefinition> readIfCompatible(CanvasTaskDefinition definition) {
        return isReadable(definition)
                ? Optional.of(deserialize(
                        definition.getDefinitionJson(), legacyTriggerInterval(definition.getTaskId())))
                : Optional.empty();
    }

    public CanvasDefinition requireReadable(CanvasTaskDefinition definition) {
        if (!isReadable(definition)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, incompatibleMessage(definition));
        }
        return deserialize(definition.getDefinitionJson(), legacyTriggerInterval(definition.getTaskId()));
    }

    private static String incompatibleMessage(CanvasTaskDefinition definition) {
        if (definition == null) {
            return "Canvas 任务定义不存在";
        }
        return "当前定义使用 Canvas %d.%d，与当前 Canvas %d.%d 不兼容，请重新配置任务定义".formatted(
                definition.getSchemaVersion(),
                definition.getSchemaMinorVersion(),
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION
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

    public CanvasDefinition deserialize(String content) {
        return deserialize(content, KafkaInputConfiguration.DEFAULT_TRIGGER_INTERVAL_SECONDS);
    }

    private CanvasDefinition deserialize(String content, int legacyTriggerIntervalSeconds) {
        try {
            CanvasDefinition definition = objectMapper.readValue(content, CanvasDefinition.class);
            CanvasDefinition upgraded = upgrader.upgradeToCurrent(
                    definition, legacyTriggerIntervalSeconds);
            validator.validate(upgraded);
            return upgraded;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法读取 Canvas 任务定义", exception);
        }
    }

    private int legacyTriggerInterval(UUID taskId) {
        return streamingConfigurationRepository.findByTaskId(taskId)
                .map(cn.superhuang.data.scalpel.business.task.domain.TaskStreamingConfiguration::getTriggerIntervalSeconds)
                .orElse(KafkaInputConfiguration.DEFAULT_TRIGGER_INTERVAL_SECONDS);
    }
}
