package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.LocalSqlTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.LocalSqlTaskInput;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskInputRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlySelectQueryParser;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Creates persistent manual runs and coordinates the transaction boundary around external JDBC work. */
@Service
public class TaskRunService {

    private static final List<TaskRunStatus> ACTIVE_STATUSES = List.of(TaskRunStatus.QUEUED, TaskRunStatus.RUNNING);

    private final DataTaskRepository taskRepository;
    private final LocalSqlTaskDefinitionRepository definitionRepository;
    private final LocalSqlTaskInputRepository inputRepository;
    private final TaskRunRepository runRepository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final LocalSqlDefinitionInspectionPort inspectionPort;
    private final DialectRegistry dialectRegistry;
    private final TaskExecutor taskExecutor;
    private final TaskRunWorker worker;
    private final ObjectMapper objectMapper;
    private final SearchEngine searchEngine;
    private final TransactionTemplate readTransactionTemplate;
    private final TransactionTemplate transactionTemplate;

    public TaskRunService(
            DataTaskRepository taskRepository,
            LocalSqlTaskDefinitionRepository definitionRepository,
            LocalSqlTaskInputRepository inputRepository,
            TaskRunRepository runRepository,
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            DataSourceRepository dataSourceRepository,
            LocalSqlDefinitionInspectionPort inspectionPort,
            DialectRegistry dialectRegistry,
            @Qualifier("localSqlTaskExecutor") TaskExecutor taskExecutor,
            TaskRunWorker worker,
            ObjectMapper objectMapper,
            SearchEngine searchEngine,
            PlatformTransactionManager transactionManager
    ) {
        this.taskRepository = taskRepository;
        this.definitionRepository = definitionRepository;
        this.inputRepository = inputRepository;
        this.runRepository = runRepository;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.inspectionPort = inspectionPort;
        this.dialectRegistry = dialectRegistry;
        this.taskExecutor = taskExecutor;
        this.worker = worker;
        this.objectMapper = objectMapper;
        this.searchEngine = searchEngine;
        this.readTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate.setReadOnly(true);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public TaskRunResponse run(UUID taskId) {
        RunPreparation preparation = readPreparation(taskId);
        LocalSqlDefinitionInspection inspection = inspectionPort.inspect(preparation.inspectionRequest());
        requireValidInspection(inspection);
        TaskRunDefinitionSnapshot snapshot = createRunSnapshot(preparation, inspection.targetColumns());
        String serializedSnapshot = writeSnapshot(snapshot);
        TaskRun created = requireTransactionResult(transactionTemplate.execute(status -> queueRun(
                taskId, preparation.definitionVersion(), serializedSnapshot
        )));
        try {
            taskExecutor.execute(() -> worker.execute(created.getId()));
        } catch (TaskRejectedException exception) {
            transactionTemplate.executeWithoutResult(status -> markQueueRejected(created.getId()));
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务执行队列已满，请稍后重试", exception);
        }
        return TaskRunResponse.from(created);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskRunResponse> search(UUID taskId, SearchRequest request) {
        requireTask(taskId);
        Page<TaskRun> page = searchEngine.search(
                request,
                TaskRun.class,
                runRepository,
                (root, query, builder) -> builder.equal(root.get("taskId"), taskId)
        );
        return new PageResponse<>(
                page.getContent().stream().map(TaskRunResponse::from).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
        );
    }

    @Transactional(readOnly = true)
    public TaskRunResponse get(UUID runId) {
        return TaskRunResponse.from(requireRun(runId));
    }

    @Transactional
    public void markInterruptedRunsFailed() {
        List<TaskRun> staleRuns = runRepository.findAllByStatusIn(ACTIVE_STATUSES);
        staleRuns.forEach(run -> run.fail("应用重启导致任务运行中断", "APPLICATION_RESTARTED"));
        if (!staleRuns.isEmpty()) {
            runRepository.saveAllAndFlush(staleRuns);
        }
    }

    private RunPreparation readPreparation(UUID taskId) {
        return requireTransactionResult(readTransactionTemplate.execute(status -> {
            DataTask task = requireTask(taskId);
            if (task.getStatus() != TaskStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布任务可以运行");
            }
            LocalSqlTaskDefinition definition = definitionRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "任务定义不存在"));
            List<UUID> inputIds = inputRepository.findAllByTaskIdOrderBySortOrderAsc(taskId).stream()
                    .map(LocalSqlTaskInput::getModelId).toList();
            Map<UUID, DataModel> models = requireModels(withOutput(inputIds, definition.getOutputModelId()));
            DataModel output = models.get(definition.getOutputModelId());
            DataSource source = requireRunnableStorage(output.getStorageDataSourceId());
            ReadOnlySelectQueryParser.parse(definition.getSqlText());
            List<LocalSqlDefinitionInspectionRequest.ModelWithFields> inputs = inputIds.stream()
                    .map(inputId -> modelWithFields(models.get(inputId))).toList();
            LocalSqlDefinitionInspectionRequest inspectionRequest = new LocalSqlDefinitionInspectionRequest(
                    source, inputs, modelWithFields(output), definition.getSqlText(), definition.getWriteMode(),
                    Duration.ofSeconds(definition.getTimeoutSeconds())
            );
            return new RunPreparation(definition.getVersion(), inputIds, output, source, definition, inspectionRequest);
        }));
    }

    private TaskRunDefinitionSnapshot createRunSnapshot(RunPreparation preparation, List<String> targetColumns) {
        DatabaseDialect dialect = dialectRegistry.require(preparation.source().getType().name());
        var config = preparation.source().getConnection().toJdbcConnectionConfig();
        return new TaskRunDefinitionSnapshot(
                preparation.source().getId(), preparation.source().getType(), preparation.inputModelIds(), preparation.output().getId(),
                new TaskRunDefinitionSnapshot.PhysicalTarget(
                        dialect.resolveCatalog(config, preparation.output().getCatalogName()),
                        dialect.resolveSchema(config, preparation.output().getSchemaName()), preparation.output().getPhysicalTableName()
                ),
                preparation.definition().getSqlText(), preparation.definition().getWriteMode(),
                preparation.definition().getTimeoutSeconds(), targetColumns
        );
    }

    private TaskRun queueRun(UUID taskId, int expectedDefinitionVersion, String snapshot) {
        DataTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (task.getStatus() != TaskStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务状态已变化，请重新运行");
        }
        LocalSqlTaskDefinition definition = definitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "任务定义不存在"));
        if (definition.getVersion() != expectedDefinitionVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务定义已变化，请重新运行");
        }
        if (runRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务已有正在执行的实例");
        }
        return runRepository.saveAndFlush(TaskRun.queue(taskId, expectedDefinitionVersion, snapshot));
    }

    private void markQueueRejected(UUID runId) {
        TaskRun run = requireRun(runId);
        run.fail("任务执行队列已满", "TASK_EXECUTOR_QUEUE_FULL");
        runRepository.saveAndFlush(run);
    }

    private LocalSqlDefinitionInspectionRequest.ModelWithFields modelWithFields(DataModel model) {
        List<DataModelField> fields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId());
        return new LocalSqlDefinitionInspectionRequest.ModelWithFields(model, fields);
    }

    private DataSource requireRunnableStorage(UUID id) {
        DataSource source = dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "模型绑定的数据存储不存在"));
        if (!source.isEnabled() || !source.getType().isJdbc() || !source.getPurposes().contains(DataSourcePurpose.STORAGE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务数据存储不可用");
        }
        return source;
    }

    private Map<UUID, DataModel> requireModels(Collection<UUID> ids) {
        Map<UUID, DataModel> models = modelRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(DataModel::getId, Function.identity()));
        if (models.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务引用的模型不存在");
        }
        return models;
    }

    private static List<UUID> withOutput(List<UUID> inputIds, UUID outputId) {
        List<UUID> ids = new ArrayList<>(inputIds);
        ids.add(outputId);
        return List.copyOf(ids);
    }

    private DataTask requireTask(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    private TaskRun requireRun(UUID id) {
        return runRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务运行不存在"));
    }

    private String writeSnapshot(TaskRunDefinitionSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法保存任务运行快照", exception);
        }
    }

    private static void requireValidInspection(LocalSqlDefinitionInspection inspection) {
        if (inspection.valid()) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, inspection.problems().stream()
                .map(LocalSqlDefinitionInspectionProblem::message)
                .collect(Collectors.joining("；")));
    }

    private static <T> T requireTransactionResult(T result) {
        if (result == null) {
            throw new IllegalStateException("事务未返回预期结果");
        }
        return result;
    }

    private record RunPreparation(
            int definitionVersion,
            List<UUID> inputModelIds,
            DataModel output,
            DataSource source,
            LocalSqlTaskDefinition definition,
            LocalSqlDefinitionInspectionRequest inspectionRequest
    ) {
    }
}
