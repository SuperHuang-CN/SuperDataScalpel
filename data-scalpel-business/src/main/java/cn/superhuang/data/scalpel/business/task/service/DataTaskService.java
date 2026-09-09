package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineSelectionService;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService.ExecutionRoute;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.lineage.service.TaskLineageSnapshotService;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.task.domain.CanvasTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.LocalSqlTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.LocalSqlTaskInput;
import cn.superhuang.data.scalpel.business.task.domain.LocalSqlWriteMode;
import cn.superhuang.data.scalpel.business.task.domain.ModelQualityTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingConfiguration;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarTaskDefinition;
import cn.superhuang.data.scalpel.business.task.repository.CanvasTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskInputRepository;
import cn.superhuang.data.scalpel.business.task.repository.ModelQualityTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskStreamingConfigurationRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskStreamingDeploymentRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskCanvasModelReferenceRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskDataSourceReferenceRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskResourceBindingRepository;
import cn.superhuang.data.scalpel.business.task.web.request.CreateDataTaskRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateDataTaskRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateLocalSqlTaskDefinitionRequest;
import cn.superhuang.data.scalpel.business.task.web.response.DataTaskResponse;
import cn.superhuang.data.scalpel.business.task.web.response.LocalSqlDefinitionValidationResponse;
import cn.superhuang.data.scalpel.business.task.web.response.LocalSqlTaskDefinitionResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskDataSourceReferenceResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskModelReferenceResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.dialect.query.InsertSelectQuery;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlySelectQueryParser;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Direct orchestration for the shared task lifecycle and type-specific definitions. */
@Service
public class DataTaskService {

    private final DataTaskRepository taskRepository;
    private final LocalSqlTaskDefinitionRepository definitionRepository;
    private final CanvasTaskDefinitionRepository canvasDefinitionRepository;
    private final ModelQualityTaskDefinitionRepository modelQualityDefinitionRepository;
    private final LocalSqlTaskInputRepository inputRepository;
    private final TaskRunRepository taskRunRepository;
    private final TaskStreamingConfigurationRepository streamingConfigurationRepository;
    private final TaskStreamingDeploymentRepository streamingDeploymentRepository;
    private final TaskCanvasModelReferenceRepository canvasModelReferenceRepository;
    private final TaskDataSourceReferenceRepository dataSourceReferenceRepository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DirectoryService directoryService;
    private final SearchEngine searchEngine;
    private final LocalSqlDefinitionInspectionPort inspectionPort;
    private final TaskScheduleService scheduleService;
    private final CanvasTaskDefinitionService canvasTaskDefinitionService;
    private final CanvasTaskRunPreparationService canvasPreparationService;
    private final ComputeEngineSelectionService computeEngineSelectionService;
    private final ComputeEngineExecutionService computeEngineExecutionService;
    private final TaskLineageSnapshotService lineageSnapshotService;
    private final LocalSqlLineageDraftFactory localSqlLineageDraftFactory;
    private final CanvasLineageDraftFactory canvasLineageDraftFactory;
    private final ModelQualityTaskDefinitionService modelQualityTaskDefinitionService;
    private final SparkJarTaskDefinitionService sparkJarTaskDefinitionService;
    private final SparkJarDevelopmentKitService sparkJarDevelopmentKitService;
    private final SparkJarTaskDefinitionRepository sparkJarDefinitionRepository;
    private final SparkJarTaskResourceBindingRepository sparkJarBindingRepository;
    private final WorkflowTaskDefinitionService workflowDefinitions;
    private final TransactionTemplate readTransactionTemplate;
    private final TransactionTemplate transactionTemplate;

    public DataTaskService(
            DataTaskRepository taskRepository,
            LocalSqlTaskDefinitionRepository definitionRepository,
            CanvasTaskDefinitionRepository canvasDefinitionRepository,
            ModelQualityTaskDefinitionRepository modelQualityDefinitionRepository,
            LocalSqlTaskInputRepository inputRepository,
            TaskRunRepository taskRunRepository,
            TaskStreamingConfigurationRepository streamingConfigurationRepository,
            TaskStreamingDeploymentRepository streamingDeploymentRepository,
            TaskCanvasModelReferenceRepository canvasModelReferenceRepository,
            TaskDataSourceReferenceRepository dataSourceReferenceRepository,
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            DataSourceRepository dataSourceRepository,
            DirectoryService directoryService,
            SearchEngine searchEngine,
            LocalSqlDefinitionInspectionPort inspectionPort,
            TaskScheduleService scheduleService,
            CanvasTaskDefinitionService canvasTaskDefinitionService,
            CanvasTaskRunPreparationService canvasPreparationService,
            ComputeEngineSelectionService computeEngineSelectionService,
            ComputeEngineExecutionService computeEngineExecutionService,
            TaskLineageSnapshotService lineageSnapshotService,
            LocalSqlLineageDraftFactory localSqlLineageDraftFactory,
            CanvasLineageDraftFactory canvasLineageDraftFactory,
            ModelQualityTaskDefinitionService modelQualityTaskDefinitionService,
            SparkJarTaskDefinitionService sparkJarTaskDefinitionService,
            SparkJarDevelopmentKitService sparkJarDevelopmentKitService,
            SparkJarTaskDefinitionRepository sparkJarDefinitionRepository,
            SparkJarTaskResourceBindingRepository sparkJarBindingRepository,
            WorkflowTaskDefinitionService workflowDefinitions,
            PlatformTransactionManager transactionManager
    ) {
        this.workflowDefinitions = workflowDefinitions;
        this.taskRepository = taskRepository;
        this.definitionRepository = definitionRepository;
        this.canvasDefinitionRepository = canvasDefinitionRepository;
        this.modelQualityDefinitionRepository = modelQualityDefinitionRepository;
        this.inputRepository = inputRepository;
        this.taskRunRepository = taskRunRepository;
        this.streamingConfigurationRepository = streamingConfigurationRepository;
        this.streamingDeploymentRepository = streamingDeploymentRepository;
        this.canvasModelReferenceRepository = canvasModelReferenceRepository;
        this.dataSourceReferenceRepository = dataSourceReferenceRepository;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.directoryService = directoryService;
        this.searchEngine = searchEngine;
        this.inspectionPort = inspectionPort;
        this.scheduleService = scheduleService;
        this.canvasTaskDefinitionService = canvasTaskDefinitionService;
        this.canvasPreparationService = canvasPreparationService;
        this.computeEngineSelectionService = computeEngineSelectionService;
        this.computeEngineExecutionService = computeEngineExecutionService;
        this.lineageSnapshotService = lineageSnapshotService;
        this.localSqlLineageDraftFactory = localSqlLineageDraftFactory;
        this.canvasLineageDraftFactory = canvasLineageDraftFactory;
        this.modelQualityTaskDefinitionService = modelQualityTaskDefinitionService;
        this.sparkJarTaskDefinitionService = sparkJarTaskDefinitionService;
        this.sparkJarDevelopmentKitService = sparkJarDevelopmentKitService;
        this.sparkJarDefinitionRepository = sparkJarDefinitionRepository;
        this.sparkJarBindingRepository = sparkJarBindingRepository;
        this.readTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate.setReadOnly(true);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public PageResponse<DataTaskResponse> search(SearchRequest request) {
        Page<DataTask> page = searchEngine.search(request, DataTask.class, taskRepository);
        List<UUID> taskIds = page.getContent().stream().map(DataTask::getId).toList();
        Map<UUID, LocalSqlTaskDefinition> definitions = definitionRepository.findAllByTaskIdIn(
                taskIds
        ).stream().collect(Collectors.toMap(LocalSqlTaskDefinition::getTaskId, Function.identity()));
        Map<UUID, CanvasTaskDefinition> canvasDefinitions = canvasDefinitionRepository.findAllByTaskIdIn(taskIds).stream()
                .collect(Collectors.toMap(CanvasTaskDefinition::getTaskId, Function.identity()));
        Map<UUID, ModelQualityTaskDefinition> qualityDefinitions = modelQualityDefinitionRepository
                .findAllByTaskIdIn(taskIds).stream()
                .collect(Collectors.toMap(ModelQualityTaskDefinition::getTaskId, Function.identity()));
        Map<UUID, String> outputModelNames = modelNames(definitions.values().stream()
                .map(LocalSqlTaskDefinition::getOutputModelId).collect(Collectors.toSet()));
        Map<UUID, String> computeEngineNames = computeEngineSelectionService.names(page.getContent().stream()
                .map(DataTask::getComputeEngineId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        return new PageResponse<>(
                page.getContent().stream().map(task -> summary(
                        task, definitions.get(task.getId()), canvasDefinitions.get(task.getId()),
                        qualityDefinitions.get(task.getId()),
                        outputModelNames, computeEngineNames
                )).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
        );
    }

    @Transactional(readOnly = true)
    public DataTaskResponse get(UUID id) {
        DataTask task = requireTask(id);
        LocalSqlTaskDefinition definition = definitionRepository.findByTaskId(id).orElse(null);
        CanvasTaskDefinition canvasDefinition = canvasDefinitionRepository.findByTaskId(id).orElse(null);
        ModelQualityTaskDefinition qualityDefinition = modelQualityDefinitionRepository.findByTaskId(id).orElse(null);
        Map<UUID, String> outputModelNames = definition == null ? Map.of() : modelNames(Set.of(definition.getOutputModelId()));
        return summary(task, definition, canvasDefinition, qualityDefinition,
                outputModelNames, computeEngineNames(task));
    }

    @Transactional(readOnly = true)
    public LocalSqlTaskDefinitionResponse getDefinition(UUID taskId) {
        requireLocalSqlTask(requireTask(taskId));
        return definitionRepository.findByTaskId(taskId)
                .map(this::definitionResponse)
                .orElseGet(() -> LocalSqlTaskDefinitionResponse.unconfigured(taskId));
    }

    @Transactional
    public DataTaskResponse create(CreateDataTaskRequest request) {
        directoryService.validateAssignment(DirectoryScope.TASK, request.directoryId());
        validateComputeEngineReference(request.type(), request.computeEngineId());
        DataTask task = taskRepository.saveAndFlush(DataTask.create(
                request.name(), request.directoryId(), request.type(), request.description(), request.computeEngineId()
        ));
        return summary(task, null, null, null, Map.of(), computeEngineNames(task));
    }

    @Transactional
    public DataTaskResponse update(UUID id, UpdateDataTaskRequest request) {
        DataTask task = requireTask(id);
        directoryService.validateAssignment(DirectoryScope.TASK, request.directoryId());
        validateComputeEngineReference(task.getType(), request.computeEngineId());
        if (task.getStatus() == TaskStatus.PUBLISHED
                && !java.util.Objects.equals(task.getComputeEngineId(), request.computeEngineId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布任务不能更换计算引擎，请先停用");
        }
        if (task.getType().isJar()
                && !java.util.Objects.equals(task.getComputeEngineId(), request.computeEngineId())) {
            sparkJarDefinitionRepository.findByTaskId(id).ifPresent(definition ->
                    sparkJarTaskDefinitionService.validateExecutionResourcesForEngine(
                            definition, request.computeEngineId()));
        }
        task.update(request.name(), request.directoryId(), request.description(), request.computeEngineId());
        DataTask saved = taskRepository.saveAndFlush(task);
        LocalSqlTaskDefinition definition = definitionRepository.findByTaskId(id).orElse(null);
        CanvasTaskDefinition canvasDefinition = canvasDefinitionRepository.findByTaskId(id).orElse(null);
        ModelQualityTaskDefinition qualityDefinition = modelQualityDefinitionRepository.findByTaskId(id).orElse(null);
        Map<UUID, String> outputModelNames = definition == null ? Map.of() : modelNames(Set.of(definition.getOutputModelId()));
        return summary(saved, definition, canvasDefinition, qualityDefinition,
                outputModelNames, computeEngineNames(saved));
    }

    @Transactional
    public LocalSqlTaskDefinitionResponse updateDefinition(UUID taskId, UpdateLocalSqlTaskDefinitionRequest request) {
        DataTask task = requireTaskForUpdate(taskId);
        requireLocalSqlTask(task);
        requireDefinitionEditable(task);
        ValidatedDefinition validated = validateForSave(request);
        LocalSqlTaskDefinition existing = definitionRepository.findByTaskId(taskId).orElse(null);
        List<UUID> previousInputIds = inputRepository.findAllByTaskIdOrderBySortOrderAsc(taskId).stream()
                .map(LocalSqlTaskInput::getModelId).toList();
        boolean changed = existing == null
                || !existing.hasSameContent(
                validated.sql(), validated.output().getId(), request.writeMode(), request.timeoutSeconds()
        )
                || !previousInputIds.equals(validated.inputIds());
        if (!changed) {
            return definitionResponse(existing);
        }
        if (existing == null) {
            existing = LocalSqlTaskDefinition.create(
                    taskId, validated.sql(), validated.output().getId(), request.writeMode(), request.timeoutSeconds()
            );
        } else {
            inputRepository.deleteAllByTaskId(taskId);
            inputRepository.flush();
            existing.update(validated.sql(), validated.output().getId(), request.writeMode(), request.timeoutSeconds());
        }
        definitionRepository.saveAndFlush(existing);
        inputRepository.saveAllAndFlush(indexedInputs(taskId, validated.inputIds()));
        return definitionResponse(existing);
    }

    public LocalSqlDefinitionValidationResponse validateDefinition(UUID taskId) {
        requireLocalSqlTask(requireTask(taskId));
        return validationResponse(inspectSnapshot(readSnapshot(taskId)));
    }

    public DataTaskResponse publish(UUID taskId) {
        DataTask current = requireTask(taskId);
        if (current.getType() == TaskType.WORKFLOW) {
            workflowDefinitions.publish(taskId, TaskStatus.DRAFT);
            scheduleService.resumeForTask(taskId);
            return get(taskId);
        }
        if (current.getType().isJar()) {
            return publishSparkJar(taskId, TaskStatus.DRAFT);
        }
        if (current.getType() == TaskType.SPARK_MODEL_QUALITY) {
            return publishModelQuality(taskId, TaskStatus.DRAFT);
        }
        if (current.getType().isCanvas()) {
            return publishCanvas(taskId);
        }
        requireLocalSqlTask(current);
        DefinitionSnapshot snapshot = readSnapshot(taskId);
        if (snapshot.status() != TaskStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有草稿任务可以发布");
        }
        LocalSqlDefinitionInspection inspection = inspectSnapshot(snapshot);
        requireValidInspection(inspection);
        LocalSqlLineageDraftFactory.BuildResult lineage = localSqlLineageDraftFactory.create(
                taskId, snapshot.definitionVersion(), snapshot.request(), inspection
        );
        DataTaskResponse response = requireTransactionResult(transactionTemplate.execute(status -> {
            DataTask task = requireTaskForUpdate(taskId);
            LocalSqlTaskDefinition definition = requireDefinition(taskId);
            if (task.getStatus() != TaskStatus.DRAFT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务状态已变化，请重新发布");
            }
            if (definition.getVersion() != snapshot.definitionVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务定义已变化，请重新校验后发布");
            }
            lineageSnapshotService.publish(lineage.draft());
            task.publish();
            return summary(
                    taskRepository.saveAndFlush(task),
                    definition,
                    modelNames(Set.of(definition.getOutputModelId()))
            );
        }));
        scheduleService.resumeForTask(taskId);
        return response;
    }

    public DataTaskResponse disable(UUID taskId) {
        DataTask current = requireTask(taskId);
        if (current.getType() == TaskType.WORKFLOW) {
            workflowDefinitions.disable(taskId);
            scheduleService.pauseForTask(taskId);
            return get(taskId);
        }
        if (current.getType().isJar()) {
            return disableSparkJar(taskId);
        }
        if (current.getType() == TaskType.SPARK_MODEL_QUALITY) {
            return disableModelQuality(taskId);
        }
        if (current.getType().isCanvas()) {
            return disableCanvas(taskId);
        }
        requireLocalSqlTask(current);
        DataTaskResponse response = requireTransactionResult(transactionTemplate.execute(status -> {
            DataTask task = requireTask(taskId);
            if (task.getStatus() != TaskStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布任务可以停用");
            }
            LocalSqlTaskDefinition definition = definitionRepository.findByTaskId(taskId).orElse(null);
            task.disable();
            return summary(taskRepository.saveAndFlush(task), definition, null, Map.of(), Map.of());
        }));
        scheduleService.pauseForTask(taskId);
        return response;
    }

    public DataTaskResponse enable(UUID taskId) {
        DataTask current = requireTask(taskId);
        if (current.getType() == TaskType.WORKFLOW) {
            workflowDefinitions.publish(taskId, TaskStatus.DISABLED);
            scheduleService.resumeForTask(taskId);
            return get(taskId);
        }
        if (current.getType().isJar()) {
            return publishSparkJar(taskId, TaskStatus.DISABLED);
        }
        if (current.getType() == TaskType.SPARK_MODEL_QUALITY) {
            return publishModelQuality(taskId, TaskStatus.DISABLED);
        }
        if (current.getType().isCanvas()) {
            return enableCanvas(taskId);
        }
        requireLocalSqlTask(current);
        DefinitionSnapshot snapshot = readSnapshot(taskId);
        if (snapshot.status() != TaskStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已停用任务可以重新启用");
        }
        LocalSqlDefinitionInspection inspection = inspectSnapshot(snapshot);
        requireValidInspection(inspection);
        LocalSqlLineageDraftFactory.BuildResult lineage = localSqlLineageDraftFactory.create(
                taskId, snapshot.definitionVersion(), snapshot.request(), inspection
        );
        DataTaskResponse response = requireTransactionResult(transactionTemplate.execute(status -> {
            DataTask task = requireTaskForUpdate(taskId);
            LocalSqlTaskDefinition definition = requireDefinition(taskId);
            if (task.getStatus() != TaskStatus.DISABLED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务状态已变化，请重新启用");
            }
            if (definition.getVersion() != snapshot.definitionVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务定义已变化，请重新校验后启用");
            }
            lineageSnapshotService.publish(lineage.draft());
            task.publish();
            return summary(
                    taskRepository.saveAndFlush(task),
                    definition,
                    modelNames(Set.of(definition.getOutputModelId()))
            );
        }));
        scheduleService.resumeForTask(taskId);
        return response;
    }

    @Transactional
    public void delete(UUID taskId) {
        DataTask task = requireTask(taskId);
        if (task.getStatus() == TaskStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布任务请先停用后再删除");
        }
        if (taskRunRepository.existsByTaskId(taskId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务已有运行记录，不能删除");
        }
        lineageSnapshotService.retireCurrent(taskId);
        scheduleService.deleteForTask(taskId);
        dataSourceReferenceRepository.deleteAllByTaskId(taskId);
        canvasModelReferenceRepository.deleteAllByTaskId(taskId);
        inputRepository.deleteAllByTaskId(taskId);
        definitionRepository.findByTaskId(taskId).ifPresent(definitionRepository::delete);
        canvasDefinitionRepository.findByTaskId(taskId).ifPresent(canvasDefinitionRepository::delete);
        modelQualityDefinitionRepository.findByTaskId(taskId).ifPresent(modelQualityDefinitionRepository::delete);
        sparkJarDevelopmentKitService.deleteForTask(taskId);
        sparkJarBindingRepository.deleteAllByTaskId(taskId);
        sparkJarDefinitionRepository.findByTaskId(taskId).ifPresent(definition -> {
            sparkJarTaskDefinitionService.deleteObjectAfterCommit(taskId, definition.getJarObjectKey());
            sparkJarDefinitionRepository.delete(definition);
        });
        workflowDefinitions.deleteDefinition(taskId);
        streamingConfigurationRepository.deleteByTaskId(taskId);
        taskRepository.delete(task);
    }

    private ValidatedDefinition validateForSave(UpdateLocalSqlTaskDefinitionRequest request) {
        InsertSelectQuery query;
        try {
            query = ReadOnlySelectQueryParser.parse(request.sql());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        List<UUID> inputIds = request.inputModelIds();
        if (new LinkedHashSet<>(inputIds).size() != inputIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "输入模型不能重复");
        }
        if (inputIds.contains(request.outputModelId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "输出模型不能同时作为输入模型");
        }
        Map<UUID, DataModel> models = requireModels(withOutput(inputIds, request.outputModelId()));
        DataModel output = models.get(request.outputModelId());
        DataSource source = requireRunnableStorage(output.getStorageDataSourceId());
        for (UUID inputId : inputIds) {
            DataModel input = models.get(inputId);
            if (!source.getId().equals(input.getStorageDataSourceId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "输入模型和输出模型必须属于同一个数据存储");
            }
        }
        if (request.writeMode() == LocalSqlWriteMode.OVERWRITE && output.getPhysicalTableMode() != PhysicalTableMode.MANAGED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OVERWRITE 只允许写入平台受控物理表");
        }
        return new ValidatedDefinition(query.sql(), List.copyOf(inputIds), output);
    }

    private void validatePersistedDefinitionLocally(DataTask task, LocalSqlTaskDefinition definition) {
        List<UUID> inputIds = inputRepository.findAllByTaskIdOrderBySortOrderAsc(task.getId()).stream()
                .map(LocalSqlTaskInput::getModelId).toList();
        validateForSave(new UpdateLocalSqlTaskDefinitionRequest(
                definition.getSqlText(), inputIds, definition.getOutputModelId(), definition.getWriteMode(), definition.getTimeoutSeconds()
        ));
    }

    private DefinitionSnapshot readSnapshot(UUID taskId) {
        return requireTransactionResult(readTransactionTemplate.execute(status -> {
            DataTask task = requireTask(taskId);
            requireLocalSqlTask(task);
            LocalSqlTaskDefinition definition = requireDefinition(taskId);
            validatePersistedDefinitionLocally(task, definition);
            List<UUID> inputIds = inputRepository.findAllByTaskIdOrderBySortOrderAsc(taskId).stream()
                    .map(LocalSqlTaskInput::getModelId).toList();
            Map<UUID, DataModel> models = requireModels(withOutput(inputIds, definition.getOutputModelId()));
            DataModel output = models.get(definition.getOutputModelId());
            DataSource source = requireRunnableStorage(output.getStorageDataSourceId());
            List<LocalSqlDefinitionInspectionRequest.ModelWithFields> inputs = inputIds.stream()
                    .map(inputId -> modelWithFieldsLoaded(models.get(inputId))).toList();
            LocalSqlDefinitionInspectionRequest request = new LocalSqlDefinitionInspectionRequest(
                    source,
                    inputs,
                    modelWithFieldsLoaded(output),
                    definition.getSqlText(),
                    definition.getWriteMode(),
                    java.time.Duration.ofSeconds(definition.getTimeoutSeconds())
            );
            return new DefinitionSnapshot(task.getStatus(), definition.getVersion(), request);
        }));
    }

    private LocalSqlDefinitionInspection inspectSnapshot(DefinitionSnapshot snapshot) {
        return inspectionPort.inspect(snapshot.request());
    }

    private LocalSqlDefinitionInspectionRequest.ModelWithFields modelWithFieldsLoaded(DataModel model) {
        List<DataModelField> fields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId());
        return new LocalSqlDefinitionInspectionRequest.ModelWithFields(model, fields);
    }

    private static void requireValidInspection(LocalSqlDefinitionInspection inspection) {
        if (inspection.valid()) {
            return;
        }
        String message = inspection.problems().stream()
                .map(LocalSqlDefinitionInspectionProblem::message)
                .collect(Collectors.joining("；"));
        throw new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private DataTaskResponse publishCanvas(UUID taskId) {
        CanvasLifecycleSnapshot snapshot = readCanvasLifecycleSnapshot(taskId);
        if (snapshot.status() != TaskStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有草稿任务可以发布");
        }
        ExecutionRoute route = snapshot.taskType() == TaskType.SPARK_STREAMING_CANVAS
                ? computeEngineExecutionService.requireStreamingRunnable(snapshot.computeEngineId())
                : computeEngineExecutionService.requireRunnable(snapshot.computeEngineId());
        CanvasTaskRunPreparationService.Preparation preparation = canvasPreparationService.prepare(
                snapshot.definition(),
                snapshot.taskType() == TaskType.SPARK_STREAMING_CANVAS
                        ? cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode.STREAMING
                        : cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode.BATCH);
        var lineageDraft = canvasLineageDraftFactory.create(
                taskId, snapshot.definitionVersion(), preparation.compilation().lineage());
        return requireTransactionResult(transactionTemplate.execute(status -> {
            DataTask task = requireTaskForUpdate(taskId);
            CanvasTaskDefinition definition = requireCanvasDefinition(taskId);
            if (task.getStatus() != TaskStatus.DRAFT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务状态已变化，请重新发布");
            }
            if (definition.getVersion() != snapshot.definitionVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Canvas 定义已变化，请重新校验后发布");
            }
            if (!route.engineId().equals(task.getComputeEngineId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务绑定的计算引擎已变化，请重新发布");
            }
            computeEngineExecutionService.assertUnchanged(route);
            canvasPreparationService.assertDataSourcesUnchanged(preparation.dataSourceVersions());
            canvasPreparationService.assertModelsUnchanged(preparation.modelVersions());
            lineageSnapshotService.publish(lineageDraft);
            task.publish();
            return summary(taskRepository.saveAndFlush(task), null, definition, Map.of());
        }));
    }

    private DataTaskResponse disableCanvas(UUID taskId) {
        return requireTransactionResult(transactionTemplate.execute(status -> {
            DataTask task = requireTask(taskId);
            if (task.getStatus() != TaskStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布任务可以停用");
            }
            if (task.getType() == TaskType.SPARK_STREAMING_CANVAS
                    && streamingDeploymentRepository.existsByTaskIdAndActualStateIn(
                    taskId,
                    List.of(
                            cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentActualState.STARTING,
                            cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentActualState.RUNNING,
                            cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentActualState.STOPPING))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "活动实时任务必须先停止，再停用");
            }
            CanvasTaskDefinition definition = canvasDefinitionRepository.findByTaskId(taskId).orElse(null);
            task.disable();
            return summary(taskRepository.saveAndFlush(task), null, definition, Map.of(), Map.of());
        }));
    }

    private DataTaskResponse enableCanvas(UUID taskId) {
        CanvasLifecycleSnapshot snapshot = readCanvasLifecycleSnapshot(taskId);
        if (snapshot.status() != TaskStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已停用任务可以重新启用");
        }
        ExecutionRoute route = snapshot.taskType() == TaskType.SPARK_STREAMING_CANVAS
                ? computeEngineExecutionService.requireStreamingRunnable(snapshot.computeEngineId())
                : computeEngineExecutionService.requireRunnable(snapshot.computeEngineId());
        CanvasTaskRunPreparationService.Preparation preparation = canvasPreparationService.prepare(
                snapshot.definition(),
                snapshot.taskType() == TaskType.SPARK_STREAMING_CANVAS
                        ? cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode.STREAMING
                        : cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode.BATCH);
        var lineageDraft = canvasLineageDraftFactory.create(
                taskId, snapshot.definitionVersion(), preparation.compilation().lineage());
        return requireTransactionResult(transactionTemplate.execute(status -> {
            DataTask task = requireTaskForUpdate(taskId);
            CanvasTaskDefinition definition = requireCanvasDefinition(taskId);
            if (task.getStatus() != TaskStatus.DISABLED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务状态已变化，请重新启用");
            }
            if (definition.getVersion() != snapshot.definitionVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Canvas 定义已变化，请重新校验后启用");
            }
            if (!route.engineId().equals(task.getComputeEngineId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务绑定的计算引擎已变化，请重新启用");
            }
            computeEngineExecutionService.assertUnchanged(route);
            canvasPreparationService.assertDataSourcesUnchanged(preparation.dataSourceVersions());
            canvasPreparationService.assertModelsUnchanged(preparation.modelVersions());
            lineageSnapshotService.publish(lineageDraft);
            task.publish();
            return summary(taskRepository.saveAndFlush(task), null, definition, Map.of());
        }));
    }

    private DataTaskResponse publishModelQuality(UUID taskId, TaskStatus expectedStatus) {
        ModelQualityTaskDefinition definition = modelQualityTaskDefinitionService.requireConfigured(taskId);
        modelQualityTaskDefinitionService.validatePublishable(taskId);
        ExecutionRoute route = computeEngineExecutionService.requireRunnable(
                requireTask(taskId).getComputeEngineId());
        DataTaskResponse response = requireTransactionResult(transactionTemplate.execute(status -> {
            DataTask task = requireTaskForUpdate(taskId);
            if (task.getType() != TaskType.SPARK_MODEL_QUALITY || task.getStatus() != expectedStatus) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务状态已变化，请重新操作");
            }
            ModelQualityTaskDefinition current = modelQualityDefinitionRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "请先选择质检目标模型"));
            if (current.getVersion() != definition.getVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "质检定义已变化，请重新操作");
            }
            if (!route.engineId().equals(task.getComputeEngineId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务绑定的计算引擎已变化，请重新操作");
            }
            computeEngineExecutionService.assertUnchanged(route);
            task.publish();
            return summary(taskRepository.saveAndFlush(task), null, null, current,
                    Map.of(), computeEngineNames(task));
        }));
        scheduleService.resumeForTask(taskId);
        return response;
    }

    private DataTaskResponse disableModelQuality(UUID taskId) {
        DataTaskResponse response = requireTransactionResult(transactionTemplate.execute(status -> {
            DataTask task = requireTaskForUpdate(taskId);
            if (task.getType() != TaskType.SPARK_MODEL_QUALITY || task.getStatus() != TaskStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布质检任务可以停用");
            }
            task.disable();
            return summary(taskRepository.saveAndFlush(task), null, null,
                    modelQualityDefinitionRepository.findByTaskId(taskId).orElse(null),
                    Map.of(), computeEngineNames(task));
        }));
        scheduleService.pauseForTask(taskId);
        return response;
    }

    private DataTaskResponse publishSparkJar(UUID taskId, TaskStatus expectedStatus) {
        SparkJarTaskDefinition definition = sparkJarTaskDefinitionService.requireConfigured(taskId);
        sparkJarTaskDefinitionService.validatePublishable(taskId);
        sparkJarTaskDefinitionService.validateArtifactAvailable(taskId);
        DataTask sourceTask = requireTask(taskId);
        ExecutionRoute route = sourceTask.getType().isStreaming()
                ? computeEngineExecutionService.requireStreamingRunnable(sourceTask.getComputeEngineId())
                : computeEngineExecutionService.requireRunnable(sourceTask.getComputeEngineId());
        DataTaskResponse response = requireTransactionResult(transactionTemplate.execute(status -> {
            DataTask task = requireTaskForUpdate(taskId);
            if (!task.getType().isJar() || task.getStatus() != expectedStatus) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务状态已变化，请重新操作");
            }
            SparkJarTaskDefinition current = sparkJarDefinitionRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "请先配置 Spark JAR 任务定义"));
            if (current.getVersion() != definition.getVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark JAR 定义已变化，请重新操作");
            }
            if (!route.engineId().equals(task.getComputeEngineId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务绑定的计算引擎已变化，请重新操作");
            }
            computeEngineExecutionService.assertUnchanged(route);
            if (task.getType() == TaskType.SPARK_JAR) {
                lineageSnapshotService.retireCurrentIfDefinitionChanged(taskId, current.getVersion());
            }
            task.publish();
            return summary(taskRepository.saveAndFlush(task), null, null, null,
                    Map.of(), computeEngineNames(task));
        }));
        if (!sourceTask.getType().isStreaming()) scheduleService.resumeForTask(taskId);
        return response;
    }

    private DataTaskResponse disableSparkJar(UUID taskId) {
        DataTaskResponse response = requireTransactionResult(transactionTemplate.execute(status -> {
            DataTask task = requireTaskForUpdate(taskId);
            if (!task.getType().isJar() || task.getStatus() != TaskStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布 Spark JAR 任务可以停用");
            }
            if (task.getType().isStreaming()
                    && streamingDeploymentRepository.existsByTaskIdAndActualStateIn(
                    taskId, List.of(
                            cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentActualState.STARTING,
                            cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentActualState.RUNNING,
                            cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentActualState.STOPPING))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "活动实时任务必须先停止，再停用");
            }
            task.disable();
            return summary(taskRepository.saveAndFlush(task), null, null, null,
                    Map.of(), computeEngineNames(task));
        }));
        if (!requireTask(taskId).getType().isStreaming()) scheduleService.pauseForTask(taskId);
        return response;
    }

    private CanvasLifecycleSnapshot readCanvasLifecycleSnapshot(UUID taskId) {
        return requireTransactionResult(readTransactionTemplate.execute(status -> {
            DataTask task = requireTask(taskId);
            if (!task.getType().isCanvas()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不是 Spark Canvas 任务");
            }
            CanvasTaskDefinition definition = requireCanvasDefinition(taskId);
            return new CanvasLifecycleSnapshot(
                    task.getType(), task.getStatus(), task.getComputeEngineId(), definition.getVersion(),
                    canvasTaskDefinitionService.requireReadable(definition));
        }));
    }

    private CanvasTaskDefinition requireCanvasDefinition(UUID taskId) {
        return canvasDefinitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "请先保存 Canvas 任务定义"));
    }

    private LocalSqlDefinitionValidationResponse validationResponse(LocalSqlDefinitionInspection inspection) {
        LocalSqlLineageDraftFactory.Assessment lineage = localSqlLineageDraftFactory.assess(inspection);
        return new LocalSqlDefinitionValidationResponse(
                inspection.valid(),
                inspection.problems().stream()
                        .map(problem -> new cn.superhuang.data.scalpel.business.task.web.response.LocalSqlDefinitionValidationProblemResponse(
                                problem.code(), problem.message(), problem.columnCode()
                        )).toList(),
                inspection.columns().stream()
                        .map(column -> new cn.superhuang.data.scalpel.business.task.web.response.LocalSqlDefinitionValidationColumnResponse(
                                column.ordinal(), column.label(), column.logicalType(), column.nativeType(), column.nullable(),
                                column.matchedOutputFieldCode()
                        )).toList(),
                inspection.targetColumns(),
                lineage.coverage().name(),
                lineage.analysisStatus().name(),
                lineage.warnings().stream().map(warning ->
                        new cn.superhuang.data.scalpel.business.task.web.response.LocalSqlLineageWarningResponse(
                                warning.code(), warning.message(), warning.outputOrdinal()
                        )).toList()
        );
    }

    private LocalSqlTaskDefinitionResponse definitionResponse(LocalSqlTaskDefinition definition) {
        List<LocalSqlTaskInput> inputs = inputRepository.findAllByTaskIdOrderBySortOrderAsc(definition.getTaskId());
        Map<UUID, DataModel> models = requireModels(withOutput(
                inputs.stream().map(LocalSqlTaskInput::getModelId).toList(), definition.getOutputModelId()
        ));
        DataModel output = models.get(definition.getOutputModelId());
        DataSource dataSource = dataSourceRepository.findById(output.getStorageDataSourceId())
                .orElseThrow(() -> new IllegalStateException("任务引用的数据存储不存在"));
        return new LocalSqlTaskDefinitionResponse(
                definition.getTaskId(), true, definition.getVersion(), definition.getSqlText(),
                inputs.stream().map(input -> TaskModelReferenceResponse.from(models.get(input.getModelId()))).toList(),
                TaskModelReferenceResponse.from(output), TaskDataSourceReferenceResponse.from(dataSource),
                definition.getWriteMode(), definition.getTimeoutSeconds(), definition.getUpdatedAt()
        );
    }

    private DataTaskResponse summary(
            DataTask task,
            LocalSqlTaskDefinition definition,
            Map<UUID, String> outputModelNames
    ) {
        return summary(task, definition, null, null, outputModelNames, computeEngineNames(task));
    }

    private DataTaskResponse summary(
            DataTask task,
            LocalSqlTaskDefinition definition,
            CanvasTaskDefinition canvasDefinition,
            Map<UUID, String> outputModelNames
    ) {
        return summary(task, definition, canvasDefinition, null, outputModelNames, computeEngineNames(task));
    }

    private DataTaskResponse summary(
            DataTask task,
            LocalSqlTaskDefinition definition,
            CanvasTaskDefinition canvasDefinition,
            Map<UUID, String> outputModelNames,
            Map<UUID, String> computeEngineNames
    ) {
        return summary(task, definition, canvasDefinition, null, outputModelNames, computeEngineNames);
    }

    private DataTaskResponse summary(
            DataTask task,
            LocalSqlTaskDefinition definition,
            CanvasTaskDefinition canvasDefinition,
            ModelQualityTaskDefinition qualityDefinition,
            Map<UUID, String> outputModelNames,
            Map<UUID, String> computeEngineNames
    ) {
        if (task.getType() == TaskType.WORKFLOW) {
            var definitionResponse = workflowDefinitions.get(task.getId());
            return DataTaskResponse.from(task, null, definitionResponse.version() != null,
                    definitionResponse.version(), null, null);
        }
        String computeEngineName = task.getComputeEngineId() == null
                ? null : computeEngineNames.get(task.getComputeEngineId());
        if (task.getType().isCanvas()) {
            return canvasDefinition == null
                    ? DataTaskResponse.from(task, computeEngineName, false, null, null, null)
                    : DataTaskResponse.from(task, computeEngineName, true, canvasDefinition.getVersion(), null, null);
        }
        if (task.getType() == TaskType.SPARK_MODEL_QUALITY) {
            if (qualityDefinition == null) {
                return DataTaskResponse.from(task, computeEngineName, false, null, null, null,
                        null, null);
            }
            String targetName = modelRepository.findById(qualityDefinition.getModelId())
                    .map(DataModel::getName).orElse(null);
            return DataTaskResponse.from(task, computeEngineName, true, qualityDefinition.getVersion(),
                    null, null, qualityDefinition.getModelId(), targetName);
        }
        if (task.getType().isJar()) {
            SparkJarTaskDefinition jarDefinition = sparkJarDefinitionRepository.findByTaskId(task.getId()).orElse(null);
            return jarDefinition == null
                    ? DataTaskResponse.from(task, computeEngineName, false, null, null, null)
                    : DataTaskResponse.from(task, computeEngineName, jarDefinition.hasJar(),
                    jarDefinition.getVersion(), null, null);
        }
        if (definition == null) return DataTaskResponse.from(task, computeEngineName, false, null, null, null);
        return DataTaskResponse.from(
                task, computeEngineName, true, definition.getVersion(), definition.getOutputModelId(),
                outputModelNames.get(definition.getOutputModelId())
        );
    }

    private Map<UUID, String> computeEngineNames(DataTask task) {
        return task.getComputeEngineId() == null
                ? Map.of() : computeEngineSelectionService.names(Set.of(task.getComputeEngineId()));
    }

    private void validateComputeEngineReference(TaskType taskType, UUID computeEngineId) {
        if (!taskType.requiresComputeEngine()) {
            if (computeEngineId != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "此任务类型不能绑定计算引擎");
            }
            return;
        }
        if (taskType.requiresComputeEngine() && computeEngineId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Spark 任务必须绑定计算引擎");
        }
        if (computeEngineId != null) {
            computeEngineSelectionService.requireExisting(computeEngineId);
        }
    }

    private DataTask requireTask(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    private DataTask requireTaskForUpdate(UUID id) {
        return taskRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    private LocalSqlTaskDefinition requireDefinition(UUID taskId) {
        return definitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "请先保存本地 SQL 任务定义"));
    }

    private static void requireLocalSqlTask(DataTask task) {
        if (task.getType() != TaskType.LOCAL_SQL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不是本地 SQL 任务");
        }
    }

    private void requireDefinitionEditable(DataTask task) {
        if (task.getStatus() != TaskStatus.DRAFT && task.getStatus() != TaskStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布任务不能修改定义，请先停用");
        }
    }

    private DataSource requireRunnableStorage(UUID id) {
        DataSource source = dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型绑定的数据存储不存在"));
        if (!source.getType().isJdbc()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "本地 SQL 任务只支持 JDBC 数据存储");
        }
        if (!source.getPurposes().contains(DataSourcePurpose.STORAGE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "本地 SQL 任务只能使用具有数据存储用途的数据源");
        }
        if (!source.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "关联的数据存储已停用");
        }
        return source;
    }

    private Map<UUID, DataModel> requireModels(Collection<UUID> ids) {
        Map<UUID, DataModel> models = modelRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(DataModel::getId, Function.identity()));
        if (models.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "输入或输出模型不存在");
        }
        return models;
    }

    private Map<UUID, String> modelNames(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        modelRepository.findAllById(ids).forEach(model -> names.put(model.getId(), model.getName()));
        return names;
    }

    private static List<UUID> withOutput(List<UUID> inputIds, UUID outputId) {
        List<UUID> all = new java.util.ArrayList<>(inputIds);
        all.add(outputId);
        return List.copyOf(all);
    }

    private static List<LocalSqlTaskInput> indexedInputs(UUID taskId, List<UUID> inputIds) {
        java.util.ArrayList<LocalSqlTaskInput> inputs = new java.util.ArrayList<>(inputIds.size());
        for (int index = 0; index < inputIds.size(); index++) {
            inputs.add(LocalSqlTaskInput.create(taskId, inputIds.get(index), index));
        }
        return List.copyOf(inputs);
    }

    private record ValidatedDefinition(String sql, List<UUID> inputIds, DataModel output) {
    }

    private record DefinitionSnapshot(
            TaskStatus status,
            int definitionVersion,
            LocalSqlDefinitionInspectionRequest request
    ) {
    }

    private record CanvasLifecycleSnapshot(
            TaskType taskType,
            TaskStatus status,
            UUID computeEngineId,
            int definitionVersion,
            cn.superhuang.data.scalpel.contract.task.CanvasDefinition definition
    ) {
    }

    private static <T> T requireTransactionResult(T result) {
        if (result == null) {
            throw new IllegalStateException("事务未返回预期结果");
        }
        return result;
    }
}
