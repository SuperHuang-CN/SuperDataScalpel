package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService.ExecutionRoute;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherExecutionLogResponse;
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
import cn.superhuang.data.scalpel.business.task.domain.TaskRunExecutionMode;
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
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunArtifactMetadataResponse;
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
import cn.superhuang.data.scalpel.contract.execution.SparkJarTrialPreview;
import cn.superhuang.data.scalpel.contract.execution.SparkJarTrialPreviewSnapshot;
import cn.superhuang.data.scalpel.contract.execution.CanvasTrialPreview;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

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

/** Creates persistent runs and coordinates type-specific execution transaction boundaries. */
@Service
public class TaskRunService {

    private static final Logger log = LoggerFactory.getLogger(TaskRunService.class);
    private static final int MAXIMUM_RESULT_ARTIFACT_BYTES = 5 * 1024 * 1024;
    private static final int MAXIMUM_TRIAL_PREVIEW_SNAPSHOT_BYTES = 4 * 1024 * 1024;
    private static final int MAXIMUM_LOG_ARTIFACT_BYTES = 20 * 1024 * 1024;
    public static final int MAXIMUM_ARTIFACT_PREVIEW_BYTES = 1024 * 1024;
    private static final int MAXIMUM_LOG_PREVIEW_LINES = 2_000;
    private static final List<TaskRunStatus> ACTIVE_STATUSES = List.of(
            TaskRunStatus.QUEUED, TaskRunStatus.RUNNING, TaskRunStatus.CANCEL_REQUESTED);

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
        return readArtifact(runId, ArtifactKind.RESULT);
    }

    public TaskRunArtifact logArtifact(UUID runId) {
        return readArtifact(runId, ArtifactKind.LOG);
    }

    public TaskRunArtifactsResponse artifacts(UUID runId) {
        ArtifactReferences references = artifactReferences(runId);
        return new TaskRunArtifactsResponse(
                runId,
                artifactMetadata(runId, references.result(), ArtifactKind.RESULT),
                artifactMetadata(runId, references.log(), ArtifactKind.LOG));
    }

    public TaskRunLogResponse logs(UUID runId) {
        RunLogReference reference = requireTransactionResult(readTransactionTemplate.execute(status -> {
            TaskRun run = requireRun(runId);
            if (!run.getTaskType().requiresComputeEngine()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务运行没有 Spark 执行日志");
            }
            return new RunLogReference(run.getId(), run.getStatus(), run.getComputeEngineId(),
                    run.getExternalExecutionId(), run.getExecutionRunId(), run.getAttempt(),
                    run.getLogObjectKey());
        }));

        boolean active = active(reference.status());
        if (!active && reference.objectKey() != null && !reference.objectKey().isBlank()) {
            TaskRunArtifactStorage storage = requireArtifactStorage();
            try {
                var metadata = storage.metadataIfPresent(reference.objectKey());
                if (metadata.isPresent()) {
                    long size = metadata.get().contentLength();
                    String content;
                    boolean truncated = false;
                    if (size <= MAXIMUM_ARTIFACT_PREVIEW_BYTES) {
                        try {
                            content = storage.readIfPresent(reference.objectKey(), MAXIMUM_ARTIFACT_PREVIEW_BYTES)
                                    .map(bytes -> new String(bytes, StandardCharsets.UTF_8)).orElse(null);
                        } catch (TaskRunArtifactStorage.ArtifactSizeLimitExceededException ignored) {
                            size = Math.max(size, MAXIMUM_ARTIFACT_PREVIEW_BYTES + 1L);
                            content = readFinalLogTail(storage, reference.objectKey(), size);
                            truncated = content != null;
                        }
                    } else {
                        content = readFinalLogTail(storage, reference.objectKey(), size);
                        truncated = content != null;
                    }
                    return new TaskRunLogResponse(reference.runId(), TaskRunLogResponse.Status.FINAL,
                            TaskRunLogResponse.Source.ARTIFACT, content, Instant.now(),
                            content == null ? null : content.getBytes(StandardCharsets.UTF_8).length,
                            truncated,
                            content == null ? "最终日志暂时无法预览，请下载查看"
                                    : truncated ? "最终日志文件较大，当前显示最近 2,000 行（最多 1 MiB）" : null,
                            size, content != null, true);
                }
            } catch (RuntimeException exception) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "任务运行日志存储当前不可用", exception);
            }
        }

        if (reference.computeEngineId() == null || reference.executionId() == null
                || reference.executionRunId() == null || reference.attempt() == null) {
            if (reference.status() == TaskRunStatus.SKIPPED) {
                return new TaskRunLogResponse(reference.runId(), TaskRunLogResponse.Status.UNAVAILABLE,
                        TaskRunLogResponse.Source.NONE, null, Instant.now(), null, false,
                        "本次运行已跳过，没有创建外部执行", null, false, false);
            }
            return new TaskRunLogResponse(reference.runId(), active
                    ? TaskRunLogResponse.Status.WAITING : TaskRunLogResponse.Status.ARCHIVING,
                    TaskRunLogResponse.Source.NONE, null, Instant.now(), null, false,
                    active ? "等待任务启动并产生日志" : "任务已结束，日志归档中",
                    null, false, false);
        }

        DispatcherExecutionLogResponse live = computeEngineExecutionService.executionLog(
                reference.computeEngineId(), reference.executionId(), reference.attempt());
        if (!reference.executionRunId().equals(live.runId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dispatcher 返回的日志不属于当前任务运行");
        }
        TaskRunLogResponse.Status status;
        if (live.status() == DispatcherExecutionLogResponse.Status.AVAILABLE) {
            status = active ? TaskRunLogResponse.Status.LIVE : TaskRunLogResponse.Status.ARCHIVING;
        } else if (live.status() == DispatcherExecutionLogResponse.Status.WAITING) {
            status = active ? TaskRunLogResponse.Status.WAITING : TaskRunLogResponse.Status.ARCHIVING;
        } else {
            status = TaskRunLogResponse.Status.UNAVAILABLE;
        }
        String message = live.message();
        if (!active && status == TaskRunLogResponse.Status.ARCHIVING) message = "任务已结束，日志归档中";
        return new TaskRunLogResponse(reference.runId(), status,
                live.status() == DispatcherExecutionLogResponse.Status.AVAILABLE
                        ? TaskRunLogResponse.Source.DISPATCHER : TaskRunLogResponse.Source.NONE,
                live.content(), live.collectedAt(), live.sizeBytes(), live.truncated(), message,
                null, false, false);
    }

    private static String readFinalLogTail(TaskRunArtifactStorage storage, String objectKey, long size) {
        long start = Math.max(0, size - MAXIMUM_ARTIFACT_PREVIEW_BYTES);
        byte[] bytes = storage.readRangeIfPresent(objectKey, start, MAXIMUM_ARTIFACT_PREVIEW_BYTES)
                .orElse(null);
        if (bytes == null) return null;
        int utf8Start = utf8Start(bytes);
        String text = new String(bytes, utf8Start, bytes.length - utf8Start, StandardCharsets.UTF_8);
        int lineStart = recentLineStart(text, MAXIMUM_LOG_PREVIEW_LINES);
        return lineStart == 0 ? text : text.substring(lineStart);
    }

    private static int utf8Start(byte[] bytes) {
        int start = 0;
        while (start < bytes.length && (bytes[start] & 0xC0) == 0x80) start++;
        return start;
    }

    private static int recentLineStart(String text, int maximumLines) {
        if (text.isEmpty()) return 0;
        int lineBreaks = 0;
        int index = text.length() - 1;
        if (text.charAt(index) == '\n') index--;
        for (; index >= 0; index--) {
            if (text.charAt(index) == '\n' && ++lineBreaks == maximumLines) return index + 1;
        }
        return 0;
    }

    public TaskRunArtifact previewArtifact(UUID runId, String kind) {
        ArtifactKind artifactKind = ArtifactKind.fromPath(kind);
        ArtifactReference reference = artifactReference(runId, artifactKind);
        try {
            TaskRunArtifactStorage.ArtifactMetadata metadata = requireArtifactStorage()
                    .metadataIfPresent(reference.objectKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, artifactKind.notReadyMessage()));
            if (metadata.contentLength() > MAXIMUM_ARTIFACT_PREVIEW_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "运行制品超过 1 MiB，无法在线预览");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务运行制品存储当前不可用", exception);
        }
        return readArtifact(runId, artifactKind, MAXIMUM_ARTIFACT_PREVIEW_BYTES);
    }

    public TaskRunArtifactStream openArtifact(UUID runId, String kind) {
        ArtifactKind artifactKind = ArtifactKind.fromPath(kind);
        ArtifactReference reference = artifactReference(runId, artifactKind);
        try {
            TaskRunArtifactStorage.ArtifactContent content = requireArtifactStorage()
                    .openIfPresent(reference.objectKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, artifactKind.notReadyMessage()));
            return new TaskRunArtifactStream(
                    content,
                    content.contentType() == null || content.contentType().isBlank()
                            ? artifactKind.contentType() : content.contentType(),
                    "task-run-%s-%s".formatted(reference.runId(), artifactKind.fileSuffix()));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务运行制品存储当前不可用", exception);
        }
    }

    public SparkJarTrialPreviewResponse trialPreview(UUID runId) {
        TrialPreviewRunReference run = trialPreviewRunReference(runId);
        if (run.executionMode() != TaskRunExecutionMode.TRIAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前运行不是在线试运行");
        }
        if (run.taskType() != TaskType.SPARK_JAR && run.taskType() != TaskType.SPARK_STREAMING_JAR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前运行不是 Spark JAR 在线试运行");
        }
        if (!active(run.status()) && run.resultObjectKey() != null) {
            SparkJarTrialPreviewResponse finalPreview = readFinalTrialPreview(runId, run);
            if (finalPreview != null) return finalPreview;
        }
        SparkJarTrialPreviewResponse snapshot = readRunningTrialPreview(runId, run);
        return snapshot == null
                ? new SparkJarTrialPreviewResponse(
                runId, run.status(), SparkJarTrialPreviewResponse.PreviewSource.NONE,
                null, null, false, null)
                : snapshot;
    }

    private SparkJarTrialPreviewResponse readFinalTrialPreview(
            UUID runId,
            TrialPreviewRunReference run
    ) {
        byte[] result = readOptionalArtifact(run.resultObjectKey(), MAXIMUM_RESULT_ARTIFACT_BYTES);
        if (result == null) return null;
        try {
            JsonNode root = objectMapper.readTree(result);
            if (!isCompatibleTrialPreviewResult(
                    root, run.taskType(), run.executionRunId(), run.executionId(), run.attempt())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前试运行结果格式不受支持");
            }
            JsonNode previewNode = root.path("trialPreview");
            SparkJarTrialPreview preview = previewNode.isMissingNode() || previewNode.isNull()
                    ? null : objectMapper.treeToValue(previewNode, SparkJarTrialPreview.class);
            Instant capturedAt = parseInstant(root.path("endedAt").asText(null));
            return new SparkJarTrialPreviewResponse(
                    runId, run.status(), SparkJarTrialPreviewResponse.PreviewSource.FINAL_RESULT,
                    null, capturedAt, true, preview);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "无法解析试运行预览结果", exception);
        }
    }

    private SparkJarTrialPreviewResponse readRunningTrialPreview(
            UUID runId,
            TrialPreviewRunReference run
    ) {
        if (run.executionRunId() == null || run.executionId() == null || run.attempt() == null) return null;
        String objectKey = "task-runs/%s/attempts/%d/trial-preview.json".formatted(
                run.executionRunId(), run.attempt());
        byte[] content = readOptionalArtifact(objectKey, MAXIMUM_TRIAL_PREVIEW_SNAPSHOT_BYTES);
        if (content == null) return null;
        try {
            SparkJarTrialPreviewSnapshot snapshot = objectMapper.readValue(
                    content, SparkJarTrialPreviewSnapshot.class);
            if (!run.executionId().equals(snapshot.executionId())
                    || !run.executionRunId().equals(snapshot.runId())
                    || run.attempt() != snapshot.attempt()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "试运行输出预览身份不匹配");
            }
            return new SparkJarTrialPreviewResponse(
                    runId, run.status(), SparkJarTrialPreviewResponse.PreviewSource.RUNNING_SNAPSHOT,
                    snapshot.revision(), snapshot.capturedAt(), false, snapshot.preview());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "无法解析运行中的试运行输出预览", exception);
        }
    }

    private byte[] readOptionalArtifact(String objectKey, int maximumBytes) {
        if (objectKey == null || objectKey.isBlank()) return null;
        try {
            return requireArtifactStorage().readIfPresent(objectKey, maximumBytes).orElse(null);
        } catch (TaskRunArtifactStorage.ArtifactSizeLimitExceededException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "试运行输出预览超过允许大小", exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "任务运行制品存储当前不可用", exception);
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public CanvasTrialPreviewResponse canvasTrialPreview(UUID runId) {
        TrialPreviewRunReference run = trialPreviewRunReference(runId);
        if (run.executionMode() != TaskRunExecutionMode.TRIAL
                || run.taskType() != TaskType.SPARK_CANVAS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前运行不是 Canvas 试运行");
        }
        if (active(run.status())) {
            return new CanvasTrialPreviewResponse(runId, run.status(), null);
        }
        byte[] result;
        try {
            result = resultArtifact(runId).content();
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                    && run.status() != TaskRunStatus.SUCCESS) {
                return new CanvasTrialPreviewResponse(runId, run.status(), null);
            }
            throw exception;
        }
        try {
            JsonNode root = objectMapper.readTree(result);
            if (!isCompatibleCanvasTrialPreviewResult(
                    root, run.taskType(), run.executionRunId(), run.executionId(), run.attempt())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前 Canvas 试运行结果格式不受支持");
            }
            JsonNode previewNode = root.path("canvasTrialPreview");
            CanvasTrialPreview preview = previewNode.isMissingNode() || previewNode.isNull()
                    ? null : objectMapper.treeToValue(previewNode, CanvasTrialPreview.class);
            return new CanvasTrialPreviewResponse(runId, run.status(), preview);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "无法解析 Canvas 试运行预览结果", exception);
        }
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

    private CanvasQueueResult queueSparkJarRun(
            UUID runId, UUID taskId, SparkJarRunSource source, String snapshot, UUID executionId,
            Instant deadline, ExecutionRoute route, String manifestKey, String manifestSha,
            String runJarKey, String resultKey, String logKey,
            SparkJarTaskRunPreparationService.Preparation preparation, TaskRunTrigger trigger,
            SparkExecutionResourceSpec executionResources
    ) {
        lockWorkflowSubmission(trigger, taskId);
        if (trigger.scheduled()) {
            TaskRun existing = runRepository.findByScheduleIdAndScheduledFireAt(
                    trigger.scheduleId(), trigger.scheduledFireAt()).orElse(null);
            if (existing != null) return CanvasQueueResult.notDispatched(existing);
            TaskSchedule schedule = scheduleRepository.findByIdForUpdate(trigger.scheduleId()).orElse(null);
            if (schedule == null || !schedule.getTaskId().equals(taskId)) return CanvasQueueResult.notDispatched(null);
            if (schedule.getStatus() != TaskScheduleStatus.ENABLED) {
                TaskRun skipped = TaskRun.scheduledSparkJarSkipped(taskId, trigger.scheduleId(),
                        source.definitionVersion(), snapshot, trigger.scheduledFireAt(),
                        "定时触发已跳过：运行计划已停用");
                return CanvasQueueResult.notDispatched(runRepository.saveAndFlush(skipped));
            }
        }
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
        if ((!trigger.scheduled() || trigger.overlapPolicy() == TaskOverlapPolicy.FORBID)
                && runRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE_STATUSES)) {
            if (trigger.scheduled()) {
                TaskRun skipped = TaskRun.scheduledSparkJarSkipped(taskId, trigger.scheduleId(),
                        source.definitionVersion(), snapshot, trigger.scheduledFireAt(),
                        "定时触发已跳过：当前任务存在正在执行的实例");
                return CanvasQueueResult.notDispatched(runRepository.saveAndFlush(skipped));
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务已有正在执行的实例");
        }
        if (!route.engineId().equals(task.getComputeEngineId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务绑定的计算引擎已变化，请重新运行");
        computeEngineExecutionService.assertUnchanged(route);
        sparkJarPreparationService.assertUnchanged(preparation);
        TaskRun run = trigger.scheduled()
                ? TaskRun.queueScheduledDispatchedSparkJar(runId, taskId, trigger.scheduleId(), trigger.scheduledFireAt(),
                source.definitionVersion(), snapshot, executionId, 1, deadline, route.engineId(), route.commandTopic())
                : TaskRun.queueDispatchedSparkJar(runId, taskId, source.definitionVersion(), snapshot,
                executionId, 1, deadline, route.engineId(), route.commandTopic());
        run.attachUserJar(current.getJarFileName(), current.getJarSha256(), current.getJarSizeBytes(), runJarKey);
        run.attachArtifacts(manifestKey, resultKey, logKey);
        trigger.attach(run);
        TaskRun saved = runRepository.saveAndFlush(run);
        executionOutboxService.enqueue(route.commandTopic(), new SubmitExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION, Instant.now(), route.engineId(),
                executionId, runId, 1, taskId, ExecutionTaskType.SPARK_JAR, source.definitionVersion(), deadline,
                new ExecutionArtifactLocation(manifestKey, manifestSha, resultKey, logKey), List.of(), 0,
                new ExecutionUserJarArtifact(runJarKey, current.getJarSha256(), current.getJarSizeBytes()),
                preparation.payload().sparkConf(), executionResources));
        return CanvasQueueResult.dispatched(saved);
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
        if (trigger.scheduled()) {
            TaskRun existing = runRepository.findByScheduleIdAndScheduledFireAt(
                    trigger.scheduleId(), trigger.scheduledFireAt()).orElse(null);
            if (existing != null) return CanvasQueueResult.notDispatched(existing);
            TaskSchedule schedule = scheduleRepository.findByIdForUpdate(trigger.scheduleId()).orElse(null);
            if (schedule == null || schedule.getStatus() != TaskScheduleStatus.ENABLED) {
                TaskRun skipped = TaskRun.scheduledModelQualitySkipped(
                        taskId, trigger.scheduleId(), source.definitionVersion(), snapshot,
                        trigger.scheduledFireAt(), "定时质检已跳过：运行计划已停用");
                skipped.captureModelQualityContext(source.modelId(), preparation.ruleSnapshotAt());
                return CanvasQueueResult.notDispatched(runRepository.saveAndFlush(skipped));
            }
        }
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
        if ((!trigger.scheduled() || trigger.overlapPolicy() == TaskOverlapPolicy.FORBID)
                && runRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE_STATUSES)) {
            if (trigger.scheduled()) {
                TaskRun skipped = TaskRun.scheduledModelQualitySkipped(
                        taskId, trigger.scheduleId(), source.definitionVersion(), snapshot,
                        trigger.scheduledFireAt(), "定时质检已跳过：当前任务存在正在执行的实例");
                skipped.captureModelQualityContext(source.modelId(), preparation.ruleSnapshotAt());
                return CanvasQueueResult.notDispatched(runRepository.saveAndFlush(skipped));
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前质检任务已有正在执行的实例");
        }
        if (!route.engineId().equals(task.getComputeEngineId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务绑定的计算引擎已变化，请重新运行");
        }
        computeEngineExecutionService.assertUnchanged(route);
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
        trigger.attach(run);
        TaskRun saved = runRepository.saveAndFlush(run);
        executionOutboxService.enqueue(route.commandTopic(), new SubmitExecutionCommand(
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
        return CanvasQueueResult.dispatched(saved);
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
        if (trigger.scheduled()) {
            TaskSchedule schedule = scheduleRepository.findByIdForUpdate(trigger.scheduleId()).orElse(null);
            TaskRun existing = runRepository.findByScheduleIdAndScheduledFireAt(
                    trigger.scheduleId(), trigger.scheduledFireAt()).orElse(null);
            if (existing != null) {
                return CanvasQueueResult.notDispatched(existing);
            }
            if (schedule == null || !schedule.getTaskId().equals(taskId)) {
                return CanvasQueueResult.notDispatched(null);
            }
            if (schedule.getStatus() != TaskScheduleStatus.ENABLED) {
                TaskRun skipped = TaskRun.scheduledCanvasSkipped(
                        taskId, trigger.scheduleId(), expectedDefinitionVersion, snapshot,
                        trigger.scheduledFireAt(), "定时触发已跳过：运行计划已停用");
                return CanvasQueueResult.notDispatched(runRepository.saveAndFlush(skipped));
            }
        }
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
        if ((!trigger.scheduled() || trigger.overlapPolicy() == TaskOverlapPolicy.FORBID)
                && runRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE_STATUSES)) {
            if (trigger.scheduled()) {
                TaskRun skipped = TaskRun.scheduledCanvasSkipped(
                        taskId, trigger.scheduleId(), expectedDefinitionVersion, snapshot,
                        trigger.scheduledFireAt(), "定时触发已跳过：当前任务存在正在执行的实例");
                return CanvasQueueResult.notDispatched(runRepository.saveAndFlush(skipped));
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务已有正在执行的实例");
        }
        if (!route.engineId().equals(task.getComputeEngineId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务绑定的计算引擎已变化，请重新运行");
        }
        computeEngineExecutionService.assertUnchanged(route);
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
        trigger.attach(run);
        TaskRun saved = runRepository.saveAndFlush(run);
        executionOutboxService.enqueue(route.commandTopic(), new SubmitExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION, Instant.now(),
                route.engineId(), executionId, runId, 1, taskId, ExecutionTaskType.SPARK_CANVAS,
                expectedDefinitionVersion, deadline,
                new ExecutionArtifactLocation(manifestKey, manifestSha256, resultKey, logKey)
        ));
        return CanvasQueueResult.dispatched(saved);
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

    private TaskRunArtifact readArtifact(UUID runId, ArtifactKind kind) {
        return readArtifact(runId, kind, kind.maximumBytes());
    }

    private TaskRunArtifact readArtifact(UUID runId, ArtifactKind kind, int maximumBytes) {
        ArtifactReference reference = artifactReference(runId, kind);
        TaskRunArtifactStorage storage = requireArtifactStorage();
        try {
            byte[] content = storage.readIfPresent(reference.objectKey(), maximumBytes)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, kind.notReadyMessage()));
            return new TaskRunArtifact(
                    content,
                    kind.contentType(),
                    "task-run-%s-%s".formatted(reference.runId(), kind.fileSuffix())
            );
        } catch (TaskRunArtifactStorage.ArtifactSizeLimitExceededException exception) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "运行制品超过允许读取大小", exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务运行制品存储当前不可用", exception);
        }
    }

    private ArtifactReferences artifactReferences(UUID runId) {
        return requireTransactionResult(readTransactionTemplate.execute(status -> {
            TaskRun run = requireRun(runId);
            if (run.getTaskType() == TaskType.LOCAL_SQL) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务运行没有 Spark 执行制品");
            }
            return new ArtifactReferences(
                    optionalArtifactReference(run, ArtifactKind.RESULT),
                    optionalArtifactReference(run, ArtifactKind.LOG));
        }));
    }

    private ArtifactReference artifactReference(UUID runId, ArtifactKind kind) {
        return requireTransactionResult(readTransactionTemplate.execute(status -> artifactReference(requireRun(runId), kind)));
    }

    private ArtifactReference artifactReference(TaskRun run, ArtifactKind kind) {
        if (run.getTaskType() == TaskType.LOCAL_SQL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务运行没有 Spark 执行制品");
        }
        String objectKey = kind == ArtifactKind.RESULT ? run.getResultObjectKey() : run.getLogObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, kind.notReadyMessage());
        }
        return new ArtifactReference(run.getId(), objectKey);
    }

    private ArtifactReference optionalArtifactReference(TaskRun run, ArtifactKind kind) {
        String objectKey = kind == ArtifactKind.RESULT ? run.getResultObjectKey() : run.getLogObjectKey();
        return objectKey == null || objectKey.isBlank() ? null : new ArtifactReference(run.getId(), objectKey);
    }

    private TaskRunArtifactMetadataResponse artifactMetadata(UUID runId, ArtifactReference reference, ArtifactKind kind) {
        String fileName = "task-run-%s-%s".formatted(runId, kind.fileSuffix());
        if (reference == null) {
            return new TaskRunArtifactMetadataResponse(kind.path(), fileName,
                    TaskRunArtifactMetadataResponse.Availability.NOT_GENERATED, null, false);
        }
        try {
            return requireArtifactStorage().metadataIfPresent(reference.objectKey())
                    .map(metadata -> new TaskRunArtifactMetadataResponse(
                            kind.path(), fileName, TaskRunArtifactMetadataResponse.Availability.AVAILABLE,
                            metadata.contentLength(), metadata.contentLength() <= MAXIMUM_ARTIFACT_PREVIEW_BYTES))
                    .orElseGet(() -> new TaskRunArtifactMetadataResponse(kind.path(), fileName,
                            TaskRunArtifactMetadataResponse.Availability.NOT_GENERATED, null, false));
        } catch (RuntimeException exception) {
            log.warn("Failed to read task-run artifact metadata: runId={} kind={}", reference.runId(), kind.path(), exception);
            return new TaskRunArtifactMetadataResponse(kind.path(), fileName,
                    TaskRunArtifactMetadataResponse.Availability.SIZE_UNAVAILABLE, null, false);
        }
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

    public record TaskRunArtifact(byte[] content, String contentType, String fileName) {
        public TaskRunArtifact {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    public record TaskRunArtifactStream(
            TaskRunArtifactStorage.ArtifactContent content,
            String contentType,
            String fileName
    ) implements AutoCloseable {
        public TaskRunArtifactStream {
            Objects.requireNonNull(content, "content");
            contentType = contentType == null || contentType.isBlank()
                    ? "application/octet-stream" : contentType;
            Objects.requireNonNull(fileName, "fileName");
        }

        public long contentLength() {
            return content.contentLength();
        }

        @Override
        public void close() throws java.io.IOException {
            content.close();
        }
    }

    private record ArtifactReference(UUID runId, String objectKey) {
    }

    private record ArtifactReferences(ArtifactReference result, ArtifactReference log) {
    }

    private record RunLogReference(
            UUID runId,
            TaskRunStatus status,
            UUID computeEngineId,
            UUID executionId,
            UUID executionRunId,
            Integer attempt,
            String objectKey
    ) {
    }

    static boolean isCompatibleTrialPreviewResult(
            JsonNode root,
            TaskType taskType,
            UUID executionRunId,
            UUID executionId,
            Integer attempt
    ) {
        int schemaVersion = root == null ? -1 : root.path("schemaVersion").asInt();
        return root != null
                && schemaVersion >= 9 && schemaVersion <= 11
                && (taskType == TaskType.SPARK_JAR || taskType == TaskType.SPARK_STREAMING_JAR)
                && executionRunId != null
                && executionId != null
                && attempt != null
                && executionRunId.toString().equals(root.path("runId").asText())
                && executionId.toString().equals(root.path("executionId").asText())
                && attempt == root.path("attempt").asInt(-1)
                && taskType.name().equals(root.path("taskType").asText());
    }

    static boolean isCompatibleCanvasTrialPreviewResult(
            JsonNode root,
            TaskType taskType,
            UUID executionRunId,
            UUID executionId,
            Integer attempt
    ) {
        int schemaVersion = root == null ? -1 : root.path("schemaVersion").asInt();
        return root != null
                && schemaVersion >= 10 && schemaVersion <= 11
                && taskType == TaskType.SPARK_CANVAS
                && executionRunId != null
                && executionId != null
                && attempt != null
                && executionRunId.toString().equals(root.path("runId").asText())
                && executionId.toString().equals(root.path("executionId").asText())
                && attempt == root.path("attempt").asInt(-1)
                && "SPARK_CANVAS".equals(root.path("taskType").asText());
    }

    private TrialPreviewRunReference trialPreviewRunReference(UUID runId) {
        return requireTransactionResult(readTransactionTemplate.execute(status -> {
            TaskRun entity = requireRun(runId);
            return new TrialPreviewRunReference(
                    entity.getExecutionMode(), entity.getStatus(), entity.getTaskType(),
                    entity.getExecutionRunId(), entity.getExternalExecutionId(), entity.getAttempt(),
                    entity.getResultObjectKey());
        }));
    }

    private static boolean active(TaskRunStatus status) {
        return status == TaskRunStatus.QUEUED || status == TaskRunStatus.RUNNING
                || status == TaskRunStatus.CANCEL_REQUESTED || status == TaskRunStatus.STOP_REQUESTED;
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

    /**
     * Identity values in result.json are generated for the dispatched execution, not the
     * management database row. Keep a scalar snapshot while the artifact is read outside the
     * transaction so failed trial runs can safely expose an empty (or partial) preview.
     */
    private record TrialPreviewRunReference(
            TaskRunExecutionMode executionMode,
            TaskRunStatus status,
            TaskType taskType,
            UUID executionRunId,
            UUID executionId,
            Integer attempt,
            String resultObjectKey
    ) {
    }

    private enum ArtifactKind {
        RESULT(MAXIMUM_RESULT_ARTIFACT_BYTES, "application/json", "result.json", "任务执行结果尚未生成"),
        LOG(MAXIMUM_LOG_ARTIFACT_BYTES, "text/plain; charset=utf-8", "console.log", "任务执行日志尚未生成");

        private final int maximumBytes;
        private final String contentType;
        private final String fileSuffix;
        private final String notReadyMessage;

        ArtifactKind(int maximumBytes, String contentType, String fileSuffix, String notReadyMessage) {
            this.maximumBytes = maximumBytes;
            this.contentType = contentType;
            this.fileSuffix = fileSuffix;
            this.notReadyMessage = notReadyMessage;
        }

        int maximumBytes() { return maximumBytes; }
        String contentType() { return contentType; }
        String fileSuffix() { return fileSuffix; }
        String notReadyMessage() { return notReadyMessage; }

        String path() { return name().toLowerCase(java.util.Locale.ROOT); }

        static ArtifactKind fromPath(String value) {
            for (ArtifactKind kind : values()) {
                if (kind.path().equals(value)) return kind;
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未知的任务运行制品类型");
        }
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
