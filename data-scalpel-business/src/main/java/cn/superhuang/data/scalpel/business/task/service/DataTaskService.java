package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.LocalSqlTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.LocalSqlTaskInput;
import cn.superhuang.data.scalpel.business.task.domain.LocalSqlWriteMode;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskInputRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
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

/** Direct orchestration for the definition lifecycle of the first LOCAL_SQL task type. */
@Service
public class DataTaskService {

    private final DataTaskRepository taskRepository;
    private final LocalSqlTaskDefinitionRepository definitionRepository;
    private final LocalSqlTaskInputRepository inputRepository;
    private final TaskRunRepository taskRunRepository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DirectoryService directoryService;
    private final SearchEngine searchEngine;
    private final LocalSqlDefinitionInspectionPort inspectionPort;
    private final TransactionTemplate readTransactionTemplate;
    private final TransactionTemplate transactionTemplate;

    public DataTaskService(
            DataTaskRepository taskRepository,
            LocalSqlTaskDefinitionRepository definitionRepository,
            LocalSqlTaskInputRepository inputRepository,
            TaskRunRepository taskRunRepository,
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            DataSourceRepository dataSourceRepository,
            DirectoryService directoryService,
            SearchEngine searchEngine,
            LocalSqlDefinitionInspectionPort inspectionPort,
            PlatformTransactionManager transactionManager
    ) {
        this.taskRepository = taskRepository;
        this.definitionRepository = definitionRepository;
        this.inputRepository = inputRepository;
        this.taskRunRepository = taskRunRepository;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.directoryService = directoryService;
        this.searchEngine = searchEngine;
        this.inspectionPort = inspectionPort;
        this.readTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate.setReadOnly(true);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public PageResponse<DataTaskResponse> search(SearchRequest request) {
        Page<DataTask> page = searchEngine.search(request, DataTask.class, taskRepository);
        Map<UUID, LocalSqlTaskDefinition> definitions = definitionRepository.findAllByTaskIdIn(
                page.getContent().stream().map(DataTask::getId).toList()
        ).stream().collect(Collectors.toMap(LocalSqlTaskDefinition::getTaskId, Function.identity()));
        Map<UUID, String> outputModelNames = modelNames(definitions.values().stream()
                .map(LocalSqlTaskDefinition::getOutputModelId).collect(Collectors.toSet()));
        return new PageResponse<>(
                page.getContent().stream().map(task -> summary(task, definitions.get(task.getId()), outputModelNames)).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
        );
    }

    @Transactional(readOnly = true)
    public DataTaskResponse get(UUID id) {
        DataTask task = requireTask(id);
        LocalSqlTaskDefinition definition = definitionRepository.findByTaskId(id).orElse(null);
        Map<UUID, String> outputModelNames = definition == null ? Map.of() : modelNames(Set.of(definition.getOutputModelId()));
        return summary(task, definition, outputModelNames);
    }

    @Transactional(readOnly = true)
    public LocalSqlTaskDefinitionResponse getDefinition(UUID taskId) {
        requireTask(taskId);
        return definitionRepository.findByTaskId(taskId)
                .map(this::definitionResponse)
                .orElseGet(() -> LocalSqlTaskDefinitionResponse.unconfigured(taskId));
    }

    @Transactional
    public DataTaskResponse create(CreateDataTaskRequest request) {
        String code = normalizeCode(request.code());
        if (taskRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务编码已存在");
        }
        directoryService.validateAssignment(DirectoryScope.TASK, request.directoryId());
        DataTask task = taskRepository.saveAndFlush(DataTask.create(code, request.name(), request.directoryId(), request.description()));
        return summary(task, null, Map.of());
    }

    @Transactional
    public DataTaskResponse update(UUID id, UpdateDataTaskRequest request) {
        DataTask task = requireTask(id);
        directoryService.validateAssignment(DirectoryScope.TASK, request.directoryId());
        task.update(request.name(), request.directoryId(), request.description());
        DataTask saved = taskRepository.saveAndFlush(task);
        LocalSqlTaskDefinition definition = definitionRepository.findByTaskId(id).orElse(null);
        Map<UUID, String> outputModelNames = definition == null ? Map.of() : modelNames(Set.of(definition.getOutputModelId()));
        return summary(saved, definition, outputModelNames);
    }

    @Transactional
    public LocalSqlTaskDefinitionResponse updateDefinition(UUID taskId, UpdateLocalSqlTaskDefinitionRequest request) {
        DataTask task = requireTask(taskId);
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
        return validationResponse(inspectSnapshot(readSnapshot(taskId)));
    }

    public DataTaskResponse publish(UUID taskId) {
        DefinitionSnapshot snapshot = readSnapshot(taskId);
        if (snapshot.status() != TaskStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有草稿任务可以发布");
        }
        LocalSqlDefinitionInspection inspection = inspectSnapshot(snapshot);
        requireValidInspection(inspection);
        return requireTransactionResult(transactionTemplate.execute(status -> {
            DataTask task = requireTask(taskId);
            LocalSqlTaskDefinition definition = requireDefinition(taskId);
            if (task.getStatus() != TaskStatus.DRAFT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务状态已变化，请重新发布");
            }
            if (definition.getVersion() != snapshot.definitionVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务定义已变化，请重新校验后发布");
            }
            task.publish();
            return summary(
                    taskRepository.saveAndFlush(task),
                    definition,
                    modelNames(Set.of(definition.getOutputModelId()))
            );
        }));
    }

    @Transactional
    public DataTaskResponse disable(UUID taskId) {
        DataTask task = requireTask(taskId);
        LocalSqlTaskDefinition definition = requireDefinition(taskId);
        if (task.getStatus() != TaskStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布任务可以停用");
        }
        task.disable();
        return summary(
                taskRepository.saveAndFlush(task),
                definition,
                modelNames(Set.of(definition.getOutputModelId()))
        );
    }

    public DataTaskResponse enable(UUID taskId) {
        DefinitionSnapshot snapshot = readSnapshot(taskId);
        if (snapshot.status() != TaskStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已停用任务可以重新启用");
        }
        LocalSqlDefinitionInspection inspection = inspectSnapshot(snapshot);
        requireValidInspection(inspection);
        return requireTransactionResult(transactionTemplate.execute(status -> {
            DataTask task = requireTask(taskId);
            LocalSqlTaskDefinition definition = requireDefinition(taskId);
            if (task.getStatus() != TaskStatus.DISABLED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务状态已变化，请重新启用");
            }
            if (definition.getVersion() != snapshot.definitionVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务定义已变化，请重新校验后启用");
            }
            task.publish();
            return summary(
                    taskRepository.saveAndFlush(task),
                    definition,
                    modelNames(Set.of(definition.getOutputModelId()))
            );
        }));
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
        inputRepository.deleteAllByTaskId(taskId);
        definitionRepository.findByTaskId(taskId).ifPresent(definitionRepository::delete);
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

    private static LocalSqlDefinitionValidationResponse validationResponse(LocalSqlDefinitionInspection inspection) {
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
                inspection.targetColumns()
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
        if (definition == null) {
            return DataTaskResponse.from(task, false, null, null, null);
        }
        return DataTaskResponse.from(
                task, true, definition.getVersion(), definition.getOutputModelId(), outputModelNames.get(definition.getOutputModelId())
        );
    }

    private DataTask requireTask(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    private LocalSqlTaskDefinition requireDefinition(UUID taskId) {
        return definitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "请先保存本地 SQL 任务定义"));
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

    private static String normalizeCode(String code) {
        return code.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private record ValidatedDefinition(String sql, List<UUID> inputIds, DataModel output) {
    }

    private record DefinitionSnapshot(
            TaskStatus status,
            int definitionVersion,
            LocalSqlDefinitionInspectionRequest request
    ) {
    }

    private static <T> T requireTransactionResult(T result) {
        if (result == null) {
            throw new IllegalStateException("事务未返回预期结果");
        }
        return result;
    }
}
