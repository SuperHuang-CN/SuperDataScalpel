package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService.ExecutionRoute;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.CanvasTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.LocalSqlTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.LocalSqlTaskInput;
import cn.superhuang.data.scalpel.business.task.domain.ModelQualityTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.operations.service.TaskRunAlertService;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskOverlapPolicy;
import cn.superhuang.data.scalpel.business.task.domain.TaskSchedule;
import cn.superhuang.data.scalpel.business.task.domain.TaskScheduleStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.CanvasTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskInputRepository;
import cn.superhuang.data.scalpel.business.task.repository.ModelQualityTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskScheduleRepository;
import cn.superhuang.data.scalpel.business.task.execution.service.TaskExecutionOutboxService;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunResponse;
import cn.superhuang.data.scalpel.business.task.web.response.SparkJarTrialPreviewResponse;
import cn.superhuang.data.scalpel.business.task.web.response.CanvasTrialPreviewResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunArtifactsResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunLogResponse;
import cn.superhuang.data.scalpel.business.task.web.request.CanvasTrialRunRequest;
import cn.superhuang.data.scalpel.contract.execution.CancelExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.ExecutionArtifactLocation;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.execution.ForceTerminateExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.SafeExecutionError;
import cn.superhuang.data.scalpel.contract.execution.SubmitExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.ExecutionUserJarArtifact;
import cn.superhuang.data.scalpel.contract.execution.SparkJarExecutionPayload;
import cn.superhuang.data.scalpel.contract.execution.CanvasTrialSpec;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlySelectQueryParser;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import cn.superhuang.data.scalpel.business.task.service.TaskRunArtifactQueryService.TaskRunArtifact;
import cn.superhuang.data.scalpel.business.task.service.TaskRunArtifactQueryService.TaskRunArtifactStream;

import org.springframework.stereotype.Service;

/** Creates persistent runs and coordinates type-specific execution transaction boundaries. */
@Service
public class TaskRunService {

    private static final Logger log = LoggerFactory.getLogger(TaskRunService.class);
    private static final List<TaskRunStatus> ACTIVE_STATUSES = List.of(
            TaskRunStatus.QUEUED, TaskRunStatus.RUNNING, TaskRunStatus.CANCEL_REQUESTED);

    private final TaskRunArtifactQueryService artifactQueries;
    private final DataTaskRepository taskRepository;
    private final LocalSqlTaskDefinitionRepository definitionRepository;
    private final CanvasTaskDefinitionRepository canvasDefinitionRepository;
    private final ModelQualityTaskDefinitionRepository modelQualityDefinitionRepository;
    private final SparkJarTaskDefinitionRepository sparkJarDefinitionRepository;
    private final LocalSqlTaskInputRepository inputRepository;
    private final TaskRunRepository runRepository;
    private final TaskRunAlertService runAlerts;
    private final TaskScheduleRepository scheduleRepository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final LocalSqlDefinitionInspectionPort inspectionPort;
    private final DialectRegistry dialectRegistry;
    private final TaskExecutor taskExecutor;
    private final WorkflowRunService workflows;
    private final TaskRunWorker worker;
    private final ObjectMapper objectMapper;
    private final SearchEngine searchEngine;
    private final CanvasTaskDefinitionService canvasDefinitionService;
    private final CanvasTaskRunPreparationService canvasPreparationService;
    private final ModelQualityTaskRunPreparationService modelQualityPreparationService;
    private final SparkJarTaskRunPreparationService sparkJarPreparationService;
    private final SparkJarTaskDefinitionService sparkJarDefinitionService;
    private final ComputeEngineExecutionService computeEngineExecutionService;
    private final TaskExecutionOutboxService executionOutboxService;
    private final ObjectProvider<TaskRunArtifactStorage> artifactStorageProvider;
    private final CanvasTaskRunProperties canvasProperties;
    private final ModelQualityTaskRunProperties modelQualityProperties;
    private final SnapshotSyncProperties snapshotSyncProperties;
    private final TransactionTemplate readTransactionTemplate;
    private final TransactionTemplate transactionTemplate;

    public TaskRunService(
            TaskRunArtifactQueryService artifactQueries,
            DataTaskRepository taskRepository,
            LocalSqlTaskDefinitionRepository definitionRepository,
            CanvasTaskDefinitionRepository canvasDefinitionRepository,
            ModelQualityTaskDefinitionRepository modelQualityDefinitionRepository,
            SparkJarTaskDefinitionRepository sparkJarDefinitionRepository,
            LocalSqlTaskInputRepository inputRepository,
            TaskRunRepository runRepository,
            TaskRunAlertService runAlerts,
            TaskScheduleRepository scheduleRepository,
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            DataSourceRepository dataSourceRepository,
            LocalSqlDefinitionInspectionPort inspectionPort,
            DialectRegistry dialectRegistry,
            @Qualifier("localSqlTaskExecutor") TaskExecutor taskExecutor,
            TaskRunWorker worker,
            ObjectMapper objectMapper,
            SearchEngine searchEngine,
            CanvasTaskDefinitionService canvasDefinitionService,
            CanvasTaskRunPreparationService canvasPreparationService,
            ModelQualityTaskRunPreparationService modelQualityPreparationService,
            SparkJarTaskRunPreparationService sparkJarPreparationService,
            SparkJarTaskDefinitionService sparkJarDefinitionService,
            ComputeEngineExecutionService computeEngineExecutionService,
            TaskExecutionOutboxService executionOutboxService,
            ObjectProvider<TaskRunArtifactStorage> artifactStorageProvider,
            CanvasTaskRunProperties canvasProperties,
            ModelQualityTaskRunProperties modelQualityProperties,
            SnapshotSyncProperties snapshotSyncProperties,
            WorkflowRunService workflows,
            PlatformTransactionManager transactionManager
    ) {
        this.artifactQueries = artifactQueries;
        this.taskRepository = taskRepository;
        this.definitionRepository = definitionRepository;
        this.canvasDefinitionRepository = canvasDefinitionRepository;
        this.modelQualityDefinitionRepository = modelQualityDefinitionRepository;
        this.sparkJarDefinitionRepository = sparkJarDefinitionRepository;
        this.inputRepository = inputRepository;
        this.runRepository = runRepository;
        this.runAlerts = runAlerts;
        this.scheduleRepository = scheduleRepository;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.inspectionPort = inspectionPort;
        this.dialectRegistry = dialectRegistry;
        this.taskExecutor = taskExecutor;
        this.workflows = workflows;
        this.worker = worker;
        this.objectMapper = objectMapper;
        this.searchEngine = searchEngine;
        this.canvasDefinitionService = canvasDefinitionService;
        this.canvasPreparationService = canvasPreparationService;
        this.modelQualityPreparationService = modelQualityPreparationService;
        this.sparkJarPreparationService = sparkJarPreparationService;
        this.sparkJarDefinitionService = sparkJarDefinitionService;
        this.computeEngineExecutionService = computeEngineExecutionService;
        this.executionOutboxService = executionOutboxService;
        this.artifactStorageProvider = artifactStorageProvider;
        this.canvasProperties = canvasProperties;
        this.modelQualityProperties = modelQualityProperties;
        this.snapshotSyncProperties = snapshotSyncProperties;
        this.readTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate.setReadOnly(true);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public TaskRunResponse run(UUID taskId) {
        TaskType type = requireTransactionResult(readTransactionTemplate.execute(status -> requireTask(taskId).getType()));
        if (type == TaskType.WORKFLOW) return workflows.queue(taskId);
        if (type.isStreaming()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Spark 实时任务请使用 Start/Stop 操作，不支持立即运行");
        }
        if (type == TaskType.SPARK_CANVAS) {
            return runCanvas(taskId);
        }
        if (type == TaskType.SPARK_MODEL_QUALITY) {
            return runModelQuality(taskId);
        }
        if (type == TaskType.SPARK_JAR) {
            return runSparkJar(taskId);
        }
        return runLocalSql(taskId, TaskRunTrigger.manual());
    }

    public TaskRunResponse submitWorkflowChild(UUID taskId, UUID parentRunId, String nodeId) {
        TaskType type = requireTransactionResult(readTransactionTemplate.execute(status -> requireTask(taskId).getType()));
        var trigger = TaskRunTrigger.workflow(parentRunId, nodeId);
        return switch (type) {
            case LOCAL_SQL -> runLocalSql(taskId, trigger);
            case SPARK_CANVAS -> submitCanvas(taskId, trigger);
            case SPARK_MODEL_QUALITY -> submitModelQuality(taskId, trigger);
            case SPARK_JAR -> submitSparkJar(taskId, trigger);
            default -> throw new ResponseStatusException(HttpStatus.CONFLICT, "工作流只支持批任务节点");
        };
    }

    private TaskRunResponse runLocalSql(UUID taskId, TaskRunTrigger trigger) {
        RunPreparation preparation = readPreparation(taskId);
        LocalSqlDefinitionInspection inspection = inspectionPort.inspect(preparation.inspectionRequest());
        requireValidInspection(inspection);
        TaskRunDefinitionSnapshot snapshot = createRunSnapshot(preparation, inspection.targetColumns());
        String serializedSnapshot = writeSnapshot(snapshot);
        TaskRun created = requireTransactionResult(transactionTemplate.execute(status -> queueRun(
                taskId, preparation.definitionVersion(), serializedSnapshot, trigger
        )));
        try {
            taskExecutor.execute(() -> worker.execute(created.getId()));
        } catch (TaskRejectedException exception) {
            transactionTemplate.executeWithoutResult(status -> markQueueRejected(created.getId()));
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务执行队列已满，请稍后重试", exception);
        }
        return TaskRunResponse.from(created);
    }

    public void runScheduled(UUID scheduleId, Instant scheduledFireAt) {
        if (scheduleId == null || scheduledFireAt == null) {
            throw new IllegalArgumentException("定时触发参数不能为空");
        }
        ScheduledCanvasRequest canvasRequest = transactionTemplate.execute(
                status -> prepareScheduledRun(scheduleId, scheduledFireAt));
        if (canvasRequest == null) {
            return;
        }
        try {
            if (canvasRequest.taskType() == TaskType.SPARK_MODEL_QUALITY) {
                submitModelQuality(canvasRequest.taskId(), canvasRequest.trigger());
            } else if (canvasRequest.taskType() == TaskType.SPARK_JAR) {
                submitSparkJar(canvasRequest.taskId(), canvasRequest.trigger());
            } else {
                submitCanvas(canvasRequest.taskId(), canvasRequest.trigger());
            }
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> recordScheduledCanvasSubmissionFailure(
                    canvasRequest, exception));
            log.warn(
                    "Spark scheduled submission failed: taskType={} taskId={} scheduleId={} scheduledFireAt={}",
                    canvasRequest.taskType(),
                    canvasRequest.taskId(), canvasRequest.trigger().scheduleId(),
                    canvasRequest.trigger().scheduledFireAt(), exception);
        }
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

    public TaskRunArtifact resultArtifact(UUID runId) {
        return artifactQueries.resultArtifact(runId);
    }

    public TaskRunArtifact logArtifact(UUID runId) {
        return artifactQueries.logArtifact(runId);
    }

    public TaskRunArtifactsResponse artifacts(UUID runId) {
        return artifactQueries.artifacts(runId);
    }

    public TaskRunLogResponse logs(UUID runId) {
        return artifactQueries.logs(runId);
    }

    public TaskRunArtifact previewArtifact(UUID runId, String kind) {
        return artifactQueries.previewArtifact(runId, kind);
    }

    public TaskRunArtifactStream openArtifact(UUID runId, String kind) {
        return artifactQueries.openArtifact(runId, kind);
    }

    public SparkJarTrialPreviewResponse trialPreview(UUID runId) {
        return artifactQueries.trialPreview(runId);
    }

    public CanvasTrialPreviewResponse canvasTrialPreview(UUID runId) {
        return artifactQueries.canvasTrialPreview(runId);
    }

    public TaskRunResponse submitCanvasTrial(UUID taskId, CanvasTrialRunRequest request) {
        if (request == null) throw new IllegalArgumentException("Canvas 试运行请求不能为空");
        CanvasTrialSpec trialSpec = new CanvasTrialSpec(
                request.targetNodeId(), request.tableName(), request.columnNames());
        CanvasTrialRunSource source = requireTransactionResult(readTransactionTemplate.execute(status -> {
            DataTask task = requireTask(taskId);
            if (task.getType() != TaskType.SPARK_CANVAS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "试运行仅支持批处理 Spark Canvas 任务");
            }
            if (task.getStatus() != TaskStatus.DRAFT && task.getStatus() != TaskStatus.DISABLED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务状态不允许 Canvas 试运行");
            }
            if (runRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE_STATUSES)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务已有正在执行的实例");
            }
            CanvasTaskDefinition persisted = canvasDefinitionRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT, "请先至少保存一次 Canvas 定义后再试运行"));
            if (persisted.getVersion() != request.baseDefinitionVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Canvas 定义版本已变化，请刷新后重试");
            }
            return new CanvasTrialRunSource(
                    persisted.getVersion(), task.getComputeEngineId(),
                    pruneCanvasTrialDefinition(request.definition(), trialSpec.targetNodeId()), trialSpec);
        }));
        CanvasDefinition trialDefinition = canvasDefinitionService.validateTrialDraft(taskId, source.definition());
        ExecutionRoute route = computeEngineExecutionService.requireRunnable(source.computeEngineId());
        CanvasTaskRunPreparationService.Preparation preparation =
                canvasPreparationService.prepareTrial(trialDefinition, trialSpec);
        TaskRunArtifactStorage storage = requireArtifactStorage();
        UUID runId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant deadline = createdAt.plus(canvasProperties.timeout()).truncatedTo(ChronoUnit.SECONDS);
        String base = "task-runs/%s/attempts/1/".formatted(runId);
        String manifestKey = base + "manifest.json";
        String resultKey = base + "result.json";
        String logKey = base + "console.log";
        CanvasTaskRunManifest manifest = new CanvasTaskRunManifest(
                CanvasTaskRunManifest.CURRENT_MANIFEST_VERSION,
                new CanvasTaskRunManifest.Execution(
                        executionId, runId, taskId, 1, source.definitionVersion(), createdAt, deadline),
                new CanvasTaskRunManifest.Task(
                        cn.superhuang.data.scalpel.contract.task.TaskType.CANVAS,
                        trialDefinition, cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode.BATCH),
                preparation.metadataSnapshot(), preparation.runtimeDataSources(),
                null, preparation.runtimeFileStorage(), preparation.runtimeFileInputs(),
                snapshotSyncProperties.toManifestLimits(), ExecutionTaskType.SPARK_CANVAS,
                null, null, null, trialSpec);
        byte[] manifestBytes = writeBytes(manifest);
        String manifestSha256 = sha256(manifestBytes);
        try {
            storage.store(manifestKey, manifestBytes, "application/json");
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "任务制品存储当前不可用", exception);
        }
        String snapshot = writeSnapshot(new CanvasRunSnapshotReference(
                1, executionId, 1, source.definitionVersion(), route.engineId(), manifestKey, manifestSha256));
        try {
            TaskRun run = requireTransactionResult(transactionTemplate.execute(status -> {
                DataTask currentTask = taskRepository.findByIdForUpdate(taskId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
                CanvasTaskDefinition currentDefinition = canvasDefinitionRepository.findByTaskId(taskId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Canvas 任务定义不存在"));
                if (currentTask.getType() != TaskType.SPARK_CANVAS
                        || currentTask.getStatus() != TaskStatus.DRAFT
                        && currentTask.getStatus() != TaskStatus.DISABLED
                        || currentDefinition.getVersion() != source.definitionVersion()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "任务状态或 Canvas 定义已变化，请重试");
                }
                if (runRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE_STATUSES)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务已有正在执行的实例");
                }
                if (!route.engineId().equals(currentTask.getComputeEngineId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "任务绑定的计算引擎已变化，请重试");
                }
                computeEngineExecutionService.assertUnchanged(route);
                canvasPreparationService.assertDataSourcesUnchanged(preparation.dataSourceVersions());
                canvasPreparationService.assertModelsUnchanged(preparation.modelVersions());
                TaskRun queued = TaskRun.queueDispatchedCanvasTrial(
                        runId, taskId, source.definitionVersion(), snapshot, executionId, 1,
                        deadline, route.engineId(), route.commandTopic());
                queued.captureCanvasTrialContext(source.trialSpec());
                queued.attachArtifacts(manifestKey, resultKey, logKey);
                TaskRun saved = runRepository.saveAndFlush(queued);
                executionOutboxService.enqueue(route.commandTopic(), new SubmitExecutionCommand(
                        1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION, Instant.now(),
                        route.engineId(), executionId, runId, 1, taskId, ExecutionTaskType.SPARK_CANVAS,
                        source.definitionVersion(), deadline,
                        new ExecutionArtifactLocation(manifestKey, manifestSha256, resultKey, logKey)));
                return saved;
            }));
            return TaskRunResponse.from(run);
        } catch (RuntimeException exception) {
            deleteOrphanManifest(storage, manifestKey, exception);
            throw exception;
        }
    }

    public TaskRunResponse cancel(UUID runId) {
        TaskType type = requireTransactionResult(readTransactionTemplate.execute(status -> requireRun(runId).getTaskType()));
        if (type == TaskType.WORKFLOW) return workflows.cancel(runId);
        if (type == TaskType.LOCAL_SQL) return workflows.cancelLocal(runId);
        return requireTransactionResult(transactionTemplate.execute(status -> {
            TaskRun run = requireRunForUpdate(runId);
            if (run.getTaskType() != TaskType.SPARK_CANVAS
                    && run.getTaskType() != TaskType.SPARK_MODEL_QUALITY
                    && run.getTaskType() != TaskType.SPARK_JAR
                    || run.getExternalExecutionId() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务运行不支持外部取消");
            }
            if (run.getStatus() == TaskRunStatus.CANCEL_REQUESTED) {
                return TaskRunResponse.from(run);
            }
            if (!ACTIVE_STATUSES.contains(run.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务运行已经结束");
            }
            run.requestCancel();
            runRepository.save(run);
            executionOutboxService.enqueue(run.getCommandTopicSnapshot(), new CancelExecutionCommand(
                    1, UUID.randomUUID(), ExecutionMessageType.CANCEL_EXECUTION, Instant.now(),
                    run.getComputeEngineId(), run.getExternalExecutionId(), run.getExecutionRunId(), run.getAttempt(),
                    "用户请求停止任务运行"
            ));
            return TaskRunResponse.from(run);
        }));
    }

    public TaskRunResponse forceTerminate(UUID runId) {
        return requireTransactionResult(transactionTemplate.execute(status -> {
            TaskRun run = requireRunForUpdate(runId);
            if (run.getTaskType() == null || !run.getTaskType().requiresComputeEngine()
                    || run.getExternalExecutionId() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务运行不支持强制终止");
            }
            if (run.getStatus() != TaskRunStatus.CANCEL_REQUESTED
                    && run.getStatus() != TaskRunStatus.STOP_REQUESTED) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "请先取消运行或正常停止，再执行强制终止");
            }
            executionOutboxService.enqueue(run.getCommandTopicSnapshot(), new ForceTerminateExecutionCommand(
                    1, UUID.randomUUID(), ExecutionMessageType.FORCE_TERMINATE_EXECUTION, Instant.now(),
                    run.getComputeEngineId(), run.getExternalExecutionId(), run.getExecutionRunId(), run.getAttempt(),
                    "用户请求强制终止任务运行"
            ));
            return TaskRunResponse.from(run);
        }));
    }

    @Transactional
    public void markInterruptedRunsFailed() {
        for (UUID id : runRepository.localRunIdsInStatuses(ACTIVE_STATUSES)) {
            runRepository.findByIdForUpdate(id).filter(run -> ACTIVE_STATUSES.contains(run.getStatus())).ifPresent(run -> {
                run.fail("应用重启导致任务运行中断", "APPLICATION_RESTARTED");
                runAlerts.capture(run);
            });
        }
    }

    private TaskRunResponse runCanvas(UUID taskId) {
        TaskRunResponse response = submitCanvas(taskId, TaskRunTrigger.manual());
        if (response == null) {
            throw new IllegalStateException("手动 Canvas 运行未创建任务实例");
        }
        return response;
    }

    private TaskRunResponse runModelQuality(UUID taskId) {
        TaskRunResponse response = submitModelQuality(taskId, TaskRunTrigger.manual());
        if (response == null) throw new IllegalStateException("手动质检运行未创建任务实例");
        return response;
    }

    private TaskRunResponse runSparkJar(UUID taskId) {
        TaskRunResponse response = submitSparkJar(taskId, TaskRunTrigger.manual());
        if (response == null) throw new IllegalStateException("手动 Spark JAR 运行未创建任务实例");
        return response;
    }

    public TaskRunResponse submitSparkJarTrial(
            UUID taskId,
            String sourceSha256,
            byte[] userJar,
            String jarSha256
    ) {
        if (sourceSha256 == null || sourceSha256.isBlank() || userJar == null || userJar.length == 0
                || !Objects.equals(jarSha256, sha256(userJar))) {
            throw new IllegalArgumentException("试运行编译制品无效");
        }
        SparkJarRunSource source = requireTransactionResult(readTransactionTemplate.execute(status -> {
            DataTask task = requireTask(taskId);
            if (task.getType() != TaskType.SPARK_JAR) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "在线试运行仅支持批处理 Spark JAR 任务");
            }
            if (task.getStatus() != TaskStatus.DRAFT && task.getStatus() != TaskStatus.DISABLED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务状态不允许在线试运行");
            }
            if (runRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE_STATUSES)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务已有正在执行的实例");
            }
            SparkJarTaskDefinition definition = sparkJarDefinitionRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Spark JAR 任务定义不存在"));
            String savedSource = definition.getOnlineSourceCode();
            if (savedSource == null || !sourceSha256.equals(sha256(savedSource.getBytes(StandardCharsets.UTF_8)))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "在线源码已变化，请重新试运行");
            }
            return new SparkJarRunSource(definition.getVersion(), task.getComputeEngineId(), definition);
        }));
        ExecutionRoute route = computeEngineExecutionService.requireRunnable(source.computeEngineId());
        SparkJarTaskRunPreparationService.Preparation preparation = sparkJarPreparationService.prepareTrial(source.definition());
        TaskRunArtifactStorage storage = requireArtifactStorage();
        UUID runId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant deadline = createdAt.plusSeconds(source.definition().getTimeoutSeconds()).truncatedTo(ChronoUnit.SECONDS);
        SparkExecutionResourceSpec executionResources = computeEngineExecutionService.resolveResources(route,
                sparkJarDefinitionService.resolvedExecutionResources(taskId, source.definition()));
        String base = "task-runs/%s/attempts/1/".formatted(runId);
        String manifestKey = base + "manifest.json";
        String runJarKey = base + "user-job.jar";
        String resultKey = base + "result.json";
        String logKey = base + "console.log";
        CanvasTaskRunManifest manifest = new CanvasTaskRunManifest(
                CanvasTaskRunManifest.CURRENT_MANIFEST_VERSION,
                new CanvasTaskRunManifest.Execution(executionId, runId, taskId, 1,
                        source.definitionVersion(), createdAt, deadline),
                null, preparation.metadataSnapshot(), preparation.runtimeDataSources(),
                null, null, List.of(), CanvasTaskRunManifest.SnapshotSyncLimits.defaults(),
                ExecutionTaskType.SPARK_JAR, null, preparation.payload(), null);
        byte[] manifestBytes = writeBytes(manifest);
        String manifestSha = sha256(manifestBytes);
        try {
            storage.store(runJarKey, userJar, "application/java-archive");
            storage.store(manifestKey, manifestBytes, "application/json");
        } catch (RuntimeException exception) {
            deleteUnusedManifest(storage, runJarKey);
            deleteUnusedManifest(storage, manifestKey);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务制品存储当前不可用", exception);
        }
        String fileName = "online-trial-" + sourceSha256.substring(0, 12) + ".jar";
        String snapshot = writeSnapshot(new SparkJarRunSnapshotReference(
                2, executionId, 1, source.definitionVersion(), route.engineId(), fileName,
                jarSha256, userJar.length, "com.example.datascalpel.ExampleSparkJob", 1,
                preparation.payload().parameters(), preparation.payload().sparkConf(),
                preparation.payload().resourceBindings(), source.definition().getTimeoutSeconds(), executionResources));
        try {
            TaskRun run = requireTransactionResult(transactionTemplate.execute(status -> {
                DataTask currentTask = taskRepository.findByIdForUpdate(taskId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
                SparkJarTaskDefinition current = sparkJarDefinitionRepository.findByTaskId(taskId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Spark JAR 任务定义不存在"));
                if (currentTask.getType() != TaskType.SPARK_JAR
                        || current.getVersion() != source.definitionVersion()
                        || current.getOnlineSourceCode() == null
                        || !sourceSha256.equals(sha256(current.getOnlineSourceCode().getBytes(StandardCharsets.UTF_8)))) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "任务定义或在线源码已变化，请重新试运行");
                }
                if (runRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE_STATUSES)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务已有正在执行的实例");
                }
                if (!route.engineId().equals(currentTask.getComputeEngineId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "任务绑定的计算引擎已变化，请重新试运行");
                }
                computeEngineExecutionService.assertUnchanged(route);
                sparkJarPreparationService.assertUnchanged(preparation);
                TaskRun queued = TaskRun.queueDispatchedSparkJarTrial(
                        runId, taskId, source.definitionVersion(), snapshot, executionId, 1,
                        deadline, route.engineId(), route.commandTopic());
                queued.attachUserJar(fileName, jarSha256, userJar.length, runJarKey);
                queued.attachArtifacts(manifestKey, resultKey, logKey);
                TaskRun saved = runRepository.saveAndFlush(queued);
                executionOutboxService.enqueue(route.commandTopic(), new SubmitExecutionCommand(
                        1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION, Instant.now(), route.engineId(),
                        executionId, runId, 1, taskId, ExecutionTaskType.SPARK_JAR,
                        source.definitionVersion(), deadline,
                        new ExecutionArtifactLocation(manifestKey, manifestSha, resultKey, logKey), List.of(), 0,
                        new ExecutionUserJarArtifact(runJarKey, jarSha256, userJar.length),
                        preparation.payload().sparkConf(), executionResources));
                return saved;
            }));
            return TaskRunResponse.from(run);
        } catch (RuntimeException exception) {
            deleteOrphanManifest(storage, manifestKey, exception);
            deleteOrphanManifest(storage, runJarKey, exception);
            throw exception;
        }
    }

    private TaskRunResponse submitSparkJar(UUID taskId, TaskRunTrigger trigger) {
        SparkJarRunSource source = requireTransactionResult(readTransactionTemplate.execute(status -> {
            DataTask task = requireTask(taskId);
            if (task.getType() != TaskType.SPARK_JAR)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不是 Spark JAR 任务");
            if (task.getStatus() != TaskStatus.PUBLISHED)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布 Spark JAR 任务可以运行");
            if (!trigger.scheduled() && runRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE_STATUSES))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务已有正在执行的实例");
            SparkJarTaskDefinition definition = sparkJarDefinitionRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Spark JAR 任务定义不存在"));
            if (!definition.hasJar()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark JAR 尚未上传");
            return new SparkJarRunSource(definition.getVersion(), task.getComputeEngineId(), definition);
        }));
        sparkJarDefinitionService.validatePublishable(taskId);
        ExecutionRoute route = computeEngineExecutionService.requireRunnable(source.computeEngineId());
        SparkJarTaskRunPreparationService.Preparation preparation = sparkJarPreparationService.prepare(
                source.definition(), trigger.scheduled() ? SparkJarExecutionPayload.TriggerType.SCHEDULED
                        : SparkJarExecutionPayload.TriggerType.MANUAL,
                trigger.scheduleId(), trigger.scheduledFireAt());
        TaskRunArtifactStorage storage = requireArtifactStorage();
        byte[] userJar = storage.readIfPresent(source.definition().getJarObjectKey(),
                        (int) SparkJarTaskDefinitionService.MAX_JAR_BYTES)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "当前用户 JAR 制品不存在，请重新上传"));
        if (userJar.length != source.definition().getJarSizeBytes()
                || !sha256(userJar).equals(source.definition().getJarSha256())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前用户 JAR 制品摘要不一致，请重新上传");
        }
        UUID runId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant deadline = createdAt.plusSeconds(source.definition().getTimeoutSeconds()).truncatedTo(ChronoUnit.SECONDS);
        SparkExecutionResourceSpec executionResources = computeEngineExecutionService.resolveResources(route,
                sparkJarDefinitionService.resolvedExecutionResources(taskId, source.definition()));
        String base = "task-runs/%s/attempts/1/".formatted(runId);
        String manifestKey = base + "manifest.json";
        String runJarKey = base + "user-job.jar";
        String resultKey = base + "result.json";
        String logKey = base + "console.log";
        CanvasTaskRunManifest manifest = new CanvasTaskRunManifest(
                CanvasTaskRunManifest.CURRENT_MANIFEST_VERSION,
                new CanvasTaskRunManifest.Execution(executionId, runId, taskId, 1,
                        source.definitionVersion(), createdAt, deadline),
                null, preparation.metadataSnapshot(), preparation.runtimeDataSources(),
                null, null, List.of(), CanvasTaskRunManifest.SnapshotSyncLimits.defaults(),
                ExecutionTaskType.SPARK_JAR, null, preparation.payload(), null);
        byte[] manifestBytes = writeBytes(manifest);
        String manifestSha = sha256(manifestBytes);
        try {
            storage.store(runJarKey, userJar, "application/java-archive");
            storage.store(manifestKey, manifestBytes, "application/json");
        } catch (RuntimeException exception) {
            deleteUnusedManifest(storage, runJarKey);
            deleteUnusedManifest(storage, manifestKey);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务制品存储当前不可用", exception);
        }
        String snapshot = writeSnapshot(new SparkJarRunSnapshotReference(
                1, executionId, 1, source.definitionVersion(), route.engineId(),
                source.definition().getJarFileName(), source.definition().getJarSha256(),
                source.definition().getJarSizeBytes(), source.definition().getJobClass(),
                source.definition().getJobApiVersion(), preparation.payload().parameters(),
                preparation.payload().sparkConf(), preparation.payload().resourceBindings(),
                source.definition().getTimeoutSeconds(), executionResources));
        try {
            CanvasQueueResult result = requireTransactionResult(transactionTemplate.execute(status -> queueSparkJarRun(
                    runId, taskId, source, snapshot, executionId, deadline, route,
                    manifestKey, manifestSha, runJarKey, resultKey, logKey, preparation, trigger, executionResources)));
            if (!result.dispatched()) {
                deleteUnusedManifest(storage, manifestKey);
                deleteUnusedManifest(storage, runJarKey);
            }
            return result.run() == null ? null : TaskRunResponse.from(result.run());
        } catch (RuntimeException exception) {
            deleteOrphanManifest(storage, manifestKey, exception);
            deleteOrphanManifest(storage, runJarKey, exception);
            throw exception;
        }
    }

    private CanvasQueueResult scheduledDispatchDecision(
            UUID taskId, TaskRunTrigger trigger, java.util.function.Function<String, TaskRun> skippedRun,
            String disabledMessage
    ) {
        if (!trigger.scheduled()) return null;
        // Serialize on the schedule before checking its unique fire time, for every task type.
        TaskSchedule schedule = scheduleRepository.findByIdForUpdate(trigger.scheduleId()).orElse(null);
        TaskRun existing = runRepository.findByScheduleIdAndScheduledFireAt(
                trigger.scheduleId(), trigger.scheduledFireAt()).orElse(null);
        if (existing != null) return CanvasQueueResult.notDispatched(existing);
        if (schedule == null || !schedule.getTaskId().equals(taskId)) return CanvasQueueResult.notDispatched(null);
        if (schedule.getStatus() != TaskScheduleStatus.ENABLED) {
            return CanvasQueueResult.notDispatched(runRepository.saveAndFlush(skippedRun.apply(disabledMessage)));
        }
        return null;
    }

    private boolean hasForbiddenOverlap(UUID taskId, TaskRunTrigger trigger) {
        return (!trigger.scheduled() || trigger.overlapPolicy() == TaskOverlapPolicy.FORBID)
                && runRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE_STATUSES);
    }

    private void requireUnchangedDispatchRoute(DataTask task, ExecutionRoute route) {
        if (!route.engineId().equals(task.getComputeEngineId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务绑定的计算引擎已变化，请重新运行");
        }
        computeEngineExecutionService.assertUnchanged(route);
    }

    /** The caller holds task/schedule locks; the run and its Submit must commit together. */
    private CanvasQueueResult commitDispatch(TaskRun run, TaskRunTrigger trigger,
                                             ExecutionRoute route, SubmitExecutionCommand command) {
        trigger.attach(run);
        TaskRun saved = runRepository.saveAndFlush(run);
        executionOutboxService.enqueue(route.commandTopic(), command);
        return CanvasQueueResult.dispatched(saved);
    }

    private CanvasQueueResult queueSparkJarRun(
            UUID runId, UUID taskId, SparkJarRunSource source, String snapshot, UUID executionId,
            Instant deadline, ExecutionRoute route, String manifestKey, String manifestSha,
            String runJarKey, String resultKey, String logKey,
            SparkJarTaskRunPreparationService.Preparation preparation, TaskRunTrigger trigger,
            SparkExecutionResourceSpec executionResources
    ) {
        lockWorkflowSubmission(trigger, taskId);
        CanvasQueueResult scheduleDecision = scheduledDispatchDecision(taskId, trigger,
                reason -> TaskRun.scheduledSparkJarSkipped(taskId, trigger.scheduleId(),
                        source.definitionVersion(), snapshot, trigger.scheduledFireAt(), reason), "定时触发已跳过：运行计划已停用");
        if (scheduleDecision != null) return scheduleDecision;
        DataTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        SparkJarTaskDefinition current = sparkJarDefinitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Spark JAR 任务定义不存在"));
        if (task.getType() != TaskType.SPARK_JAR || task.getStatus() != TaskStatus.PUBLISHED
                || current.getVersion() != source.definitionVersion()
                || !Objects.equals(current.getJarSha256(), source.definition().getJarSha256())) {
            if (trigger.scheduled()) {
                TaskRun skipped = TaskRun.scheduledSparkJarSkipped(taskId, trigger.scheduleId(),
                        source.definitionVersion(), snapshot, trigger.scheduledFireAt(),
                        "定时触发已跳过：任务或 JAR 定义已变化");
                return CanvasQueueResult.notDispatched(runRepository.saveAndFlush(skipped));
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark JAR 任务或定义已变化，请重新运行");
        }
        if (hasForbiddenOverlap(taskId, trigger)) {
            if (trigger.scheduled()) {
                TaskRun skipped = TaskRun.scheduledSparkJarSkipped(taskId, trigger.scheduleId(),
                        source.definitionVersion(), snapshot, trigger.scheduledFireAt(),
                        "定时触发已跳过：当前任务存在正在执行的实例");
                return CanvasQueueResult.notDispatched(runRepository.saveAndFlush(skipped));
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务已有正在执行的实例");
        }
        requireUnchangedDispatchRoute(task, route);
        sparkJarPreparationService.assertUnchanged(preparation);
        TaskRun run = trigger.scheduled()
                ? TaskRun.queueScheduledDispatchedSparkJar(runId, taskId, trigger.scheduleId(), trigger.scheduledFireAt(),
                source.definitionVersion(), snapshot, executionId, 1, deadline, route.engineId(), route.commandTopic())
                : TaskRun.queueDispatchedSparkJar(runId, taskId, source.definitionVersion(), snapshot,
                executionId, 1, deadline, route.engineId(), route.commandTopic());
        run.attachUserJar(current.getJarFileName(), current.getJarSha256(), current.getJarSizeBytes(), runJarKey);
        run.attachArtifacts(manifestKey, resultKey, logKey);
        return commitDispatch(run, trigger, route, new SubmitExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION, Instant.now(), route.engineId(),
                executionId, runId, 1, taskId, ExecutionTaskType.SPARK_JAR, source.definitionVersion(), deadline,
                new ExecutionArtifactLocation(manifestKey, manifestSha, resultKey, logKey), List.of(), 0,
                new ExecutionUserJarArtifact(runJarKey, current.getJarSha256(), current.getJarSizeBytes()),
                preparation.payload().sparkConf(), executionResources));
    }

    private TaskRunResponse submitModelQuality(UUID taskId, TaskRunTrigger trigger) {
        QualityRunSource source = requireTransactionResult(readTransactionTemplate.execute(status -> {
            DataTask task = requireTask(taskId);
            if (task.getType() != TaskType.SPARK_MODEL_QUALITY) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不是 Spark 模型质检任务");
            }
            if (task.getStatus() != TaskStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布质检任务可以运行");
            }
            if (!trigger.scheduled() && runRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE_STATUSES)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前质检任务已有正在执行的实例");
            }
            ModelQualityTaskDefinition definition = modelQualityDefinitionRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "质检任务定义不存在"));
            return new QualityRunSource(
                    definition.getVersion(), definition.getModelId(), task.getComputeEngineId(),
                    definition.getFailureSampleLimit());
        }));
        ExecutionRoute route = computeEngineExecutionService.requireRunnable(source.computeEngineId());
        ModelQualityTaskRunPreparationService.Preparation preparation =
                modelQualityPreparationService.prepare(source.modelId(), source.failureSampleLimit());
        if (preparation.payload().rules().isEmpty()) {
            if (!trigger.scheduled()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "目标模型当前没有可执行的质量规则");
            }
            return requireTransactionResult(transactionTemplate.execute(status -> {
                String snapshot = writeSnapshot(new QualityRunSnapshotReference(
                        1, null, 1, source.definitionVersion(), route.engineId(), null, null,
                        preparation.payload().skippedRules().size()));
                TaskRun skipped = TaskRun.scheduledModelQualitySkipped(
                        taskId, trigger.scheduleId(), source.definitionVersion(), snapshot,
                        trigger.scheduledFireAt(), "定时质检已跳过：当前没有可执行规则");
                skipped.captureModelQualityContext(source.modelId(), preparation.ruleSnapshotAt());
                return TaskRunResponse.from(runRepository.saveAndFlush(skipped));
            }));
        }
        TaskRunArtifactStorage storage = requireArtifactStorage();
        UUID runId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant deadline = createdAt.plus(modelQualityProperties.timeout()).truncatedTo(ChronoUnit.SECONDS);
        String base = "task-runs/%s/attempts/1/".formatted(runId);
        String manifestKey = base + "manifest.json";
        String resultKey = base + "result.json";
        String logKey = base + "console.log";
        CanvasTaskRunManifest manifest = new CanvasTaskRunManifest(
                CanvasTaskRunManifest.CURRENT_MANIFEST_VERSION,
                new CanvasTaskRunManifest.Execution(
                        executionId, runId, taskId, 1, source.definitionVersion(), createdAt, deadline),
                null, null, preparation.runtimeDataSources(), null, null, List.of(),
                CanvasTaskRunManifest.SnapshotSyncLimits.defaults(),
                ExecutionTaskType.SPARK_MODEL_QUALITY, preparation.payload());
        byte[] manifestBytes = writeBytes(manifest);
        String digest = sha256(manifestBytes);
        try {
            storage.store(manifestKey, manifestBytes, "application/json");
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务制品存储当前不可用", exception);
        }
        String snapshot = writeSnapshot(new QualityRunSnapshotReference(
                1, executionId, 1, source.definitionVersion(), route.engineId(), manifestKey, digest,
                preparation.payload().skippedRules().size()));
        try {
            CanvasQueueResult queued = requireTransactionResult(transactionTemplate.execute(status ->
                    queueModelQualityRun(runId, taskId, source, snapshot, executionId, deadline, route,
                            manifestKey, digest, resultKey, logKey, preparation, trigger)));
            if (!queued.dispatched()) deleteUnusedManifest(storage, manifestKey);
            return queued.run() == null ? null : TaskRunResponse.from(queued.run());
        } catch (RuntimeException exception) {
            deleteOrphanManifest(storage, manifestKey, exception);
            throw exception;
        }
    }

    private CanvasQueueResult queueModelQualityRun(
            UUID runId,
            UUID taskId,
            QualityRunSource source,
            String snapshot,
            UUID executionId,
            Instant deadline,
            ExecutionRoute route,
            String manifestKey,
            String manifestSha256,
            String resultKey,
            String logKey,
            ModelQualityTaskRunPreparationService.Preparation preparation,
            TaskRunTrigger trigger
    ) {
        lockWorkflowSubmission(trigger, taskId);
        CanvasQueueResult scheduleDecision = scheduledDispatchDecision(taskId, trigger,
                reason -> {
                    TaskRun skipped = TaskRun.scheduledModelQualitySkipped(taskId, trigger.scheduleId(),
                            source.definitionVersion(), snapshot, trigger.scheduledFireAt(), reason);
                    skipped.captureModelQualityContext(source.modelId(), preparation.ruleSnapshotAt());
                    return skipped;
                }, "定时质检已跳过：运行计划已停用");
        if (scheduleDecision != null) return scheduleDecision;
        DataTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        ModelQualityTaskDefinition definition = modelQualityDefinitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "质检任务定义不存在"));
        if (task.getType() != TaskType.SPARK_MODEL_QUALITY || task.getStatus() != TaskStatus.PUBLISHED
                || definition.getVersion() != source.definitionVersion()) {
            if (trigger.scheduled()) {
                TaskRun skipped = TaskRun.scheduledModelQualitySkipped(
                        taskId, trigger.scheduleId(), source.definitionVersion(), snapshot,
                        trigger.scheduledFireAt(), "定时质检已跳过：任务或定义已变化");
                skipped.captureModelQualityContext(source.modelId(), preparation.ruleSnapshotAt());
                return CanvasQueueResult.notDispatched(runRepository.saveAndFlush(skipped));
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "质检任务或定义已变化，请重新运行");
        }
        if (hasForbiddenOverlap(taskId, trigger)) {
            if (trigger.scheduled()) {
                TaskRun skipped = TaskRun.scheduledModelQualitySkipped(
                        taskId, trigger.scheduleId(), source.definitionVersion(), snapshot,
                        trigger.scheduledFireAt(), "定时质检已跳过：当前任务存在正在执行的实例");
                skipped.captureModelQualityContext(source.modelId(), preparation.ruleSnapshotAt());
                return CanvasQueueResult.notDispatched(runRepository.saveAndFlush(skipped));
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前质检任务已有正在执行的实例");
        }
        requireUnchangedDispatchRoute(task, route);
        modelQualityPreparationService.assertUnchanged(preparation);
        TaskRun run = trigger.scheduled()
                ? TaskRun.queueScheduledDispatchedModelQuality(
                runId, taskId, trigger.scheduleId(), trigger.scheduledFireAt(), source.definitionVersion(),
                snapshot, executionId, 1, deadline, route.engineId(), route.commandTopic())
                : TaskRun.queueDispatchedModelQuality(
                runId, taskId, source.definitionVersion(), snapshot, executionId, 1, deadline,
                route.engineId(), route.commandTopic());
        run.captureModelQualityContext(source.modelId(), preparation.ruleSnapshotAt());
        run.attachArtifacts(manifestKey, resultKey, logKey);
        return commitDispatch(run, trigger, route, new SubmitExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION, Instant.now(),
                route.engineId(), executionId, runId, 1, taskId, ExecutionTaskType.SPARK_MODEL_QUALITY,
                source.definitionVersion(), deadline,
                new ExecutionArtifactLocation(manifestKey, manifestSha256, resultKey, logKey),
                preparation.payload().failureSampleLimit() == 0 ? List.of()
                        : preparation.payload().rules().stream()
                        .filter(rule -> rule.type() != cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleType.ROW_COUNT
                                && rule.type() != cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleType.FRESHNESS)
                        .map(cn.superhuang.data.scalpel.contract.quality.ModelQualityExecutionPayload.QualityRuleSnapshot::id)
                        .toList(), preparation.payload().failureSampleLimit()));
    }

    private TaskRunResponse submitCanvas(UUID taskId, TaskRunTrigger trigger) {
        CanvasRunSource source = requireTransactionResult(readTransactionTemplate.execute(status -> {
            DataTask task = requireTask(taskId);
            if (task.getType() != TaskType.SPARK_CANVAS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不是 Spark Canvas 任务");
            }
            if (task.getStatus() != TaskStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布任务可以运行");
            }
            if (!trigger.scheduled() && runRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE_STATUSES)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务已有正在执行的实例");
            }
            CanvasTaskDefinition definition = canvasDefinitionRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Canvas 任务定义不存在"));
            return new CanvasRunSource(
                    definition.getVersion(), canvasDefinitionService.requireReadable(definition),
                    task.getComputeEngineId());
        }));
        ExecutionRoute route = computeEngineExecutionService.requireRunnable(source.computeEngineId());
        CanvasTaskRunPreparationService.Preparation preparation = canvasPreparationService.prepare(source.definition());
        TaskRunArtifactStorage storage = requireArtifactStorage();
        UUID runId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant deadline = Instant.now()
                .plus(canvasProperties.timeout())
                .truncatedTo(ChronoUnit.SECONDS);
        String base = "task-runs/%s/attempts/1/".formatted(runId);
        String manifestKey = base + "manifest.json";
        String resultKey = base + "result.json";
        String logKey = base + "console.log";
        CanvasTaskRunManifest manifest = new CanvasTaskRunManifest(
                CanvasTaskRunManifest.CURRENT_MANIFEST_VERSION,
                new CanvasTaskRunManifest.Execution(
                        executionId, runId, taskId, 1, source.definitionVersion(), createdAt, deadline),
                new CanvasTaskRunManifest.Task(
                        cn.superhuang.data.scalpel.contract.task.TaskType.CANVAS,
                        source.definition()),
                preparation.metadataSnapshot(),
                preparation.runtimeDataSources(),
                null,
                preparation.runtimeFileStorage(),
                preparation.runtimeFileInputs(),
                snapshotSyncProperties.toManifestLimits()
        );
        byte[] manifestBytes = writeBytes(manifest);
        String manifestSha256 = sha256(manifestBytes);
        try {
            storage.store(manifestKey, manifestBytes, "application/json");
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务制品存储当前不可用", exception);
        }
        String snapshot = writeSnapshot(new CanvasRunSnapshotReference(
                1, executionId, 1, source.definitionVersion(), route.engineId(), manifestKey, manifestSha256));
        try {
            CanvasQueueResult queueResult = requireTransactionResult(transactionTemplate.execute(status -> queueCanvasRun(
                    runId, taskId, source.definitionVersion(), snapshot, executionId, deadline, route,
                    manifestKey, manifestSha256, resultKey, logKey,
                    preparation.dataSourceVersions(),
                    preparation.modelVersions(),
                    trigger
            )));
            if (!queueResult.dispatched()) {
                deleteUnusedManifest(storage, manifestKey);
            }
            return queueResult.run() == null ? null : TaskRunResponse.from(queueResult.run());
        } catch (RuntimeException exception) {
            deleteOrphanManifest(storage, manifestKey, exception);
            throw exception;
        }
    }

    private CanvasQueueResult queueCanvasRun(
            UUID runId,
            UUID taskId,
            int expectedDefinitionVersion,
            String snapshot,
            UUID executionId,
            Instant deadline,
            ExecutionRoute route,
            String manifestKey,
            String manifestSha256,
            String resultKey,
            String logKey,
            Map<UUID, Instant> dataSourceVersions,
            Map<UUID, CanvasTaskRunPreparationService.ModelVersion> modelVersions,
            TaskRunTrigger trigger
    ) {
        lockWorkflowSubmission(trigger, taskId);
        CanvasQueueResult scheduleDecision = scheduledDispatchDecision(taskId, trigger,
                reason -> TaskRun.scheduledCanvasSkipped(taskId, trigger.scheduleId(),
                        expectedDefinitionVersion, snapshot, trigger.scheduledFireAt(), reason), "定时触发已跳过：运行计划已停用");
        if (scheduleDecision != null) return scheduleDecision;
        DataTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (task.getType() != TaskType.SPARK_CANVAS || task.getStatus() != TaskStatus.PUBLISHED) {
            if (trigger.scheduled()) {
                TaskRun skipped = TaskRun.scheduledCanvasSkipped(
                        taskId, trigger.scheduleId(), expectedDefinitionVersion, snapshot,
                        trigger.scheduledFireAt(), "定时触发已跳过：任务不再处于已发布状态");
                return CanvasQueueResult.notDispatched(runRepository.saveAndFlush(skipped));
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务状态已变化，请重新运行");
        }
        CanvasTaskDefinition definition = canvasDefinitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Canvas 任务定义不存在"));
        if (definition.getVersion() != expectedDefinitionVersion) {
            if (trigger.scheduled()) {
                TaskRun skipped = TaskRun.scheduledCanvasSkipped(
                        taskId, trigger.scheduleId(), expectedDefinitionVersion, snapshot,
                        trigger.scheduledFireAt(), "定时触发已跳过：Canvas 定义版本已变化");
                return CanvasQueueResult.notDispatched(runRepository.saveAndFlush(skipped));
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Canvas 定义已变化，请重新运行");
        }
        if (hasForbiddenOverlap(taskId, trigger)) {
            if (trigger.scheduled()) {
                TaskRun skipped = TaskRun.scheduledCanvasSkipped(
                        taskId, trigger.scheduleId(), expectedDefinitionVersion, snapshot,
                        trigger.scheduledFireAt(), "定时触发已跳过：当前任务存在正在执行的实例");
                return CanvasQueueResult.notDispatched(runRepository.saveAndFlush(skipped));
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务已有正在执行的实例");
        }
        requireUnchangedDispatchRoute(task, route);
        canvasPreparationService.assertDataSourcesUnchanged(dataSourceVersions);
        canvasPreparationService.assertModelsUnchanged(modelVersions);
        TaskRun run = trigger.scheduled()
                ? TaskRun.queueScheduledDispatchedCanvas(
                        runId, taskId, trigger.scheduleId(), trigger.scheduledFireAt(),
                        expectedDefinitionVersion, snapshot, executionId, 1, deadline,
                        route.engineId(), route.commandTopic())
                : TaskRun.queueDispatchedCanvas(
                        runId, taskId, expectedDefinitionVersion, snapshot, executionId, 1, deadline,
                        route.engineId(), route.commandTopic());
        run.attachArtifacts(manifestKey, resultKey, logKey);
        return commitDispatch(run, trigger, route, new SubmitExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION, Instant.now(),
                route.engineId(), executionId, runId, 1, taskId, ExecutionTaskType.SPARK_CANVAS,
                expectedDefinitionVersion, deadline,
                new ExecutionArtifactLocation(manifestKey, manifestSha256, resultKey, logKey)
        ));
    }

    private RunPreparation readPreparation(UUID taskId) {
        return requireTransactionResult(readTransactionTemplate.execute(status -> {
            DataTask task = requireTask(taskId);
            requireLocalSqlTask(task);
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

    private TaskRun queueRun(UUID taskId, int expectedDefinitionVersion, String snapshot, TaskRunTrigger trigger) {
        lockWorkflowSubmission(trigger, taskId);
        DataTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        requireLocalSqlTask(task);
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
        var run = TaskRun.queue(taskId, expectedDefinitionVersion, snapshot);
        trigger.attach(run);
        return runRepository.saveAndFlush(run);
    }

    private ScheduledCanvasRequest prepareScheduledRun(UUID scheduleId, Instant scheduledFireAt) {
        TaskSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId).orElse(null);
        if (schedule == null || schedule.getStatus() != TaskScheduleStatus.ENABLED) {
            return null;
        }
        if (runRepository.findByScheduleIdAndScheduledFireAt(scheduleId, scheduledFireAt).isPresent()) {
            return null;
        }
        DataTask task = taskRepository.findByIdForUpdate(schedule.getTaskId()).orElse(null);
        if (task == null || task.getStatus() != TaskStatus.PUBLISHED) {
            return null;
        }
        if (task.getType() == TaskType.WORKFLOW) {
            workflows.queueScheduled(task, schedule, scheduledFireAt);
            return null;
        }
        if (task.getType() == TaskType.SPARK_CANVAS) {
            return prepareScheduledCanvasRun(task, schedule, scheduledFireAt);
        }
        if (task.getType() == TaskType.SPARK_MODEL_QUALITY) {
            ModelQualityTaskDefinition definition = modelQualityDefinitionRepository
                    .findByTaskId(task.getId()).orElse(null);
            int version = definition == null ? 0 : definition.getVersion();
            if (definition == null) {
                String snapshot = writeSnapshot(new ScheduledCanvasSubmissionSnapshot(1, 0, null));
                TaskRun skipped = TaskRun.scheduledModelQualitySkipped(
                        task.getId(), schedule.getId(), version, snapshot, scheduledFireAt,
                        "定时质检已跳过：质检任务定义不存在");
                runRepository.saveAndFlush(skipped);
                return null;
            }
            return new ScheduledCanvasRequest(
                    task.getId(),
                    TaskRunTrigger.scheduled(schedule.getId(), scheduledFireAt, schedule.getOverlapPolicy()),
                    version,
                    writeSnapshot(new ScheduledCanvasSubmissionSnapshot(1, version, definition.getModelId().toString())),
                    TaskType.SPARK_MODEL_QUALITY,
                    definition.getModelId());
        }
        if (task.getType() == TaskType.SPARK_JAR) {
            SparkJarTaskDefinition definition = sparkJarDefinitionRepository.findByTaskId(task.getId()).orElse(null);
            int version = definition == null ? 0 : definition.getVersion();
            String snapshot = writeSnapshot(new ScheduledCanvasSubmissionSnapshot(
                    1, version, definition == null ? null : definition.getJarSha256()));
            if (definition == null || !definition.hasJar()) {
                TaskRun skipped = TaskRun.scheduledSparkJarSkipped(task.getId(), schedule.getId(), version,
                        snapshot, scheduledFireAt, "定时触发已跳过：Spark JAR 任务定义不存在或未上传 JAR");
                runRepository.saveAndFlush(skipped);
                return null;
            }
            if (schedule.getOverlapPolicy() == TaskOverlapPolicy.FORBID
                    && runRepository.existsByTaskIdAndStatusIn(task.getId(), ACTIVE_STATUSES)) {
                TaskRun skipped = TaskRun.scheduledSparkJarSkipped(task.getId(), schedule.getId(), version,
                        snapshot, scheduledFireAt, "定时触发已跳过：当前任务存在正在执行的实例");
                runRepository.saveAndFlush(skipped);
                return null;
            }
            return new ScheduledCanvasRequest(task.getId(), TaskRunTrigger.scheduled(
                    schedule.getId(), scheduledFireAt, schedule.getOverlapPolicy()), version,
                    snapshot, TaskType.SPARK_JAR, null);
        }
        if (task.getType() != TaskType.LOCAL_SQL) {
            return null;
        }
        LocalSqlTaskDefinition definition = definitionRepository.findByTaskId(task.getId()).orElse(null);
        if (definition == null) {
            return null;
        }
        List<UUID> inputIds = inputRepository.findAllByTaskIdOrderBySortOrderAsc(task.getId()).stream()
                .map(LocalSqlTaskInput::getModelId).toList();
        String snapshot = writeScheduledSnapshot(new ScheduledTaskRunSnapshot(
                inputIds, definition.getOutputModelId(), definition.getSqlText(), definition.getWriteMode(),
                definition.getTimeoutSeconds()
        ));
        boolean shouldSkip = schedule.getOverlapPolicy() == TaskOverlapPolicy.FORBID
                && runRepository.existsByTaskIdAndStatusIn(task.getId(), ACTIVE_STATUSES);
        TaskRun run = shouldSkip
                ? TaskRun.scheduledSkipped(task.getId(), scheduleId, definition.getVersion(), snapshot, scheduledFireAt)
                : TaskRun.scheduledSuccess(task.getId(), scheduleId, definition.getVersion(), snapshot, scheduledFireAt);
        runRepository.saveAndFlush(run);
        return null;
    }

    private ScheduledCanvasRequest prepareScheduledCanvasRun(
            DataTask task,
            TaskSchedule schedule,
            Instant scheduledFireAt
    ) {
        CanvasTaskDefinition definition = canvasDefinitionRepository.findByTaskId(task.getId()).orElse(null);
        int definitionVersion = definition == null ? 0 : definition.getVersion();
        String snapshot = writeSnapshot(new ScheduledCanvasSubmissionSnapshot(
                1, definitionVersion, definition == null ? null : definition.getDefinitionJson()));
        if (definition == null) {
            TaskRun failed = TaskRun.failedScheduledCanvasSubmission(
                    task.getId(), schedule.getId(), definitionVersion, snapshot, scheduledFireAt,
                    new SafeExecutionError("CANVAS_DEFINITION_MISSING", "Canvas 任务定义不存在"));
            runRepository.saveAndFlush(failed);
        runAlerts.capture(failed);
            return null;
        }
        if (schedule.getOverlapPolicy() == TaskOverlapPolicy.FORBID
                && runRepository.existsByTaskIdAndStatusIn(task.getId(), ACTIVE_STATUSES)) {
            TaskRun skipped = TaskRun.scheduledCanvasSkipped(
                    task.getId(), schedule.getId(), definitionVersion, snapshot, scheduledFireAt,
                    "定时触发已跳过：当前任务存在正在执行的实例");
            runRepository.saveAndFlush(skipped);
            return null;
        }
        return new ScheduledCanvasRequest(
                task.getId(),
                TaskRunTrigger.scheduled(schedule.getId(), scheduledFireAt, schedule.getOverlapPolicy()),
                definitionVersion,
                snapshot,
                TaskType.SPARK_CANVAS,
                null
        );
    }

    private void recordScheduledCanvasSubmissionFailure(
            ScheduledCanvasRequest request,
            RuntimeException exception
    ) {
        UUID scheduleId = request.trigger().scheduleId();
        Instant scheduledFireAt = request.trigger().scheduledFireAt();
        if (runRepository.findByScheduleIdAndScheduledFireAt(scheduleId, scheduledFireAt).isPresent()) {
            return;
        }
        if (!taskRepository.existsById(request.taskId())) {
            return;
        }
        boolean quality = request.taskType() == TaskType.SPARK_MODEL_QUALITY;
        boolean sparkJar = request.taskType() == TaskType.SPARK_JAR;
        String message = exception instanceof ResponseStatusException responseStatusException
                && responseStatusException.getReason() != null
                && !responseStatusException.getReason().isBlank()
                ? responseStatusException.getReason()
                : quality ? "Spark 模型质检定时提交失败"
                : sparkJar ? "Spark JAR 定时提交失败" : "Spark Canvas 定时提交失败";
        TaskRun failed = TaskRun.failedScheduledCanvasSubmission(
                request.taskId(), scheduleId, request.definitionVersion(), request.failureSnapshot(),
                scheduledFireAt,
                new SafeExecutionError(
                        quality ? "MODEL_QUALITY_SCHEDULE_SUBMISSION_FAILED"
                                : sparkJar ? "SPARK_JAR_SCHEDULE_SUBMISSION_FAILED" : "CANVAS_SCHEDULE_SUBMISSION_FAILED",
                        message)
        );
        if (quality) {
            failed.useTaskType(TaskType.SPARK_MODEL_QUALITY);
            if (request.qualityTargetModelId() != null) {
                failed.captureModelQualityContext(request.qualityTargetModelId(), null);
            }
        }
        if (sparkJar) failed.useTaskType(TaskType.SPARK_JAR);
        runRepository.saveAndFlush(failed);
        runAlerts.capture(failed);
    }

    private void markQueueRejected(UUID runId) {
        TaskRun run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务运行不存在"));
        run.fail("任务执行队列已满", "TASK_EXECUTOR_QUEUE_FULL");
        runAlerts.capture(run);
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

    private static void requireLocalSqlTask(DataTask task) {
        if (task.getType() != TaskType.LOCAL_SQL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不是本地 SQL 任务");
        }
    }

    private TaskRun requireRun(UUID id) {
        return runRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务运行不存在"));
    }

    private TaskRun requireRunForUpdate(UUID id) {
        return runRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务运行不存在"));
    }

    private TaskRunArtifactStorage requireArtifactStorage() {
        TaskRunArtifactStorage storage = artifactStorageProvider.getIfAvailable();
        if (storage == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务制品存储尚未配置");
        }
        return storage;
    }

    private static void deleteOrphanManifest(
            TaskRunArtifactStorage storage,
            String manifestKey,
            RuntimeException originalFailure
    ) {
        try {
            storage.delete(manifestKey);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            log.warn("Failed to delete orphan Canvas manifest object {}", manifestKey, cleanupFailure);
        }
    }

    private static void deleteUnusedManifest(TaskRunArtifactStorage storage, String manifestKey) {
        try {
            storage.delete(manifestKey);
        } catch (RuntimeException cleanupFailure) {
            log.warn("Failed to delete unused Canvas manifest object {}", manifestKey, cleanupFailure);
        }
    }

    private String writeSnapshot(Object snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法保存任务运行快照", exception);
        }
    }

    private String writeScheduledSnapshot(ScheduledTaskRunSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法保存定时任务运行快照", exception);
        }
    }

    private byte[] writeBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法创建 Canvas 任务 manifest", exception);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
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

    private record CanvasRunSource(
            int definitionVersion,
            cn.superhuang.data.scalpel.contract.task.CanvasDefinition definition,
            UUID computeEngineId
    ) {
    }

    private record CanvasTrialRunSource(
            int definitionVersion,
            UUID computeEngineId,
            CanvasDefinition definition,
            CanvasTrialSpec trialSpec
    ) {
    }

    private record QualityRunSource(
            int definitionVersion,
            UUID modelId,
            UUID computeEngineId,
            int failureSampleLimit
    ) {
    }

    private record SparkJarRunSource(
            int definitionVersion,
            UUID computeEngineId,
            SparkJarTaskDefinition definition
    ) {}

    private void lockWorkflowSubmission(TaskRunTrigger trigger, UUID taskId) {
        if (trigger.parentRunId() != null) workflows.lockForSubmission(trigger.parentRunId(), trigger.workflowNodeId(), taskId);
    }

    private record TaskRunTrigger(
            UUID scheduleId,
            Instant scheduledFireAt,
            TaskOverlapPolicy overlapPolicy,
            UUID parentRunId,
            String workflowNodeId
    ) {
        static TaskRunTrigger manual() {
            return new TaskRunTrigger(null, null, null, null, null);
        }

        static TaskRunTrigger scheduled(
                UUID scheduleId,
                Instant scheduledFireAt,
                TaskOverlapPolicy overlapPolicy
        ) {
            if (scheduleId == null || scheduledFireAt == null || overlapPolicy == null) {
                throw new IllegalArgumentException("Canvas 定时运行触发信息不能为空");
            }
            return new TaskRunTrigger(scheduleId, scheduledFireAt, overlapPolicy, null, null);
        }

        static TaskRunTrigger workflow(UUID parentRunId, String nodeId) {
            return new TaskRunTrigger(null, null, null, Objects.requireNonNull(parentRunId), Objects.requireNonNull(nodeId));
        }
        void attach(TaskRun run) {
            if (parentRunId != null) run.attachWorkflow(parentRunId, workflowNodeId);
        }
        boolean scheduled() { return scheduleId != null; }
    }

    private record ScheduledCanvasRequest(
            UUID taskId,
            TaskRunTrigger trigger,
            int definitionVersion,
            String failureSnapshot,
            TaskType taskType,
            UUID qualityTargetModelId
    ) {
    }

    private record CanvasQueueResult(TaskRun run, boolean dispatched) {
        static CanvasQueueResult dispatched(TaskRun run) {
            return new CanvasQueueResult(run, true);
        }

        static CanvasQueueResult notDispatched(TaskRun run) {
            return new CanvasQueueResult(run, false);
        }
    }

    private record ScheduledCanvasSubmissionSnapshot(
            int schemaVersion,
            int definitionVersion,
            String definitionJson
    ) {
    }

    private static CanvasDefinition pruneCanvasTrialDefinition(
            CanvasDefinition definition,
            String targetNodeId
    ) {
        if (definition == null || definition.nodes() == null || definition.edges() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Canvas 试运行定义不完整");
        }
        Map<String, CanvasNodeDefinition> nodesById = new LinkedHashMap<>();
        for (CanvasNodeDefinition node : definition.nodes()) {
            if (node == null || node.id() == null || node.id().isBlank()
                    || nodesById.putIfAbsent(node.id(), node) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Canvas 节点 ID 为空或重复");
            }
        }
        CanvasNodeDefinition target = nodesById.get(targetNodeId);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "试运行目标节点不存在");
        }
        if (isOutputNode(target.nodeType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "第一阶段不支持试运行到 Output 节点");
        }
        Map<String, List<String>> predecessors = new LinkedHashMap<>();
        nodesById.keySet().forEach(nodeId -> predecessors.put(nodeId, new ArrayList<>()));
        for (CanvasEdgeDefinition edge : definition.edges()) {
            if (edge == null || edge.sourceNodeId() == null || edge.targetNodeId() == null
                    || !nodesById.containsKey(edge.sourceNodeId())
                    || !nodesById.containsKey(edge.targetNodeId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Canvas 连线引用了无效节点");
            }
            predecessors.get(edge.targetNodeId()).add(edge.sourceNodeId());
        }
        Set<String> included = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(targetNodeId);
        while (!pending.isEmpty()) {
            String nodeId = pending.removeFirst();
            if (!included.add(nodeId)) continue;
            predecessors.getOrDefault(nodeId, List.of()).forEach(pending::addLast);
        }
        return new CanvasDefinition(
                definition.schemaVersion(),
                definition.schemaMinorVersion(),
                definition.nodes().stream().filter(node -> included.contains(node.id())).toList(),
                definition.edges().stream().filter(edge -> included.contains(edge.sourceNodeId())
                        && included.contains(edge.targetNodeId())).toList());
    }

    private static boolean isOutputNode(CanvasNodeType type) {
        return switch (type) {
            case MODEL_OUTPUT, MODEL_SNAPSHOT_SYNC_OUTPUT,
                    JDBC_OUTPUT, JDBC_SNAPSHOT_SYNC_OUTPUT,
                    KAFKA_OUTPUT, FILE_OUTPUT -> true;
            default -> false;
        };
    }

    private record CanvasRunSnapshotReference(
            int schemaVersion,
            UUID executionId,
            int attempt,
            int definitionVersion,
            UUID computeEngineId,
            String manifestObjectKey,
            String manifestSha256
    ) {
    }

    private record QualityRunSnapshotReference(
            int schemaVersion,
            UUID executionId,
            int attempt,
            int definitionVersion,
            UUID computeEngineId,
            String manifestObjectKey,
            String manifestSha256,
            int skippedRules
    ) {
    }

    private record SparkJarRunSnapshotReference(
            int schemaVersion,
            UUID executionId,
            int attempt,
            int definitionVersion,
            UUID computeEngineId,
            String jarFileName,
            String jarSha256,
            long jarSizeBytes,
            String jobClass,
            int jobApiVersion,
            List<SparkJarExecutionPayload.Parameter> parameters,
            List<cn.superhuang.data.scalpel.contract.execution.SparkConfigurationEntry> sparkConf,
            List<SparkJarExecutionPayload.ResourceBinding> resourceBindings,
            int timeoutSeconds,
            SparkExecutionResourceSpec executionResources
    ) {}
}
