package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherExecutionLogResponse;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunExecutionMode;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.web.response.SparkJarTrialPreviewResponse;
import cn.superhuang.data.scalpel.business.task.web.response.CanvasTrialPreviewResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunArtifactMetadataResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunArtifactsResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunLogResponse;
import cn.superhuang.data.scalpel.contract.execution.SparkJarTrialPreview;
import cn.superhuang.data.scalpel.contract.execution.SparkJarTrialPreviewSnapshot;
import cn.superhuang.data.scalpel.contract.execution.CanvasTrialPreview;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Creates persistent runs and coordinates type-specific execution transaction boundaries. */
@Service
public class TaskRunArtifactQueryService {

    private final TaskRunRepository runRepository;
    private final ComputeEngineExecutionService computeEngineExecutionService;
    private final ObjectProvider<TaskRunArtifactStorage> artifactStorageProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate readTransactionTemplate;

    public TaskRunArtifactQueryService(TaskRunRepository runRepository,
                                       ComputeEngineExecutionService computeEngineExecutionService,
                                       ObjectProvider<TaskRunArtifactStorage> artifactStorageProvider,
                                       ObjectMapper objectMapper,
                                       PlatformTransactionManager transactionManager) {
        this.runRepository = runRepository;
        this.computeEngineExecutionService = computeEngineExecutionService;
        this.artifactStorageProvider = artifactStorageProvider;
        this.objectMapper = objectMapper;
        this.readTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate.setReadOnly(true);
    }


    private static final Logger log = LoggerFactory.getLogger(TaskRunArtifactQueryService.class);
    private static final int MAXIMUM_RESULT_ARTIFACT_BYTES = 5 * 1024 * 1024;
    private static final int MAXIMUM_TRIAL_PREVIEW_SNAPSHOT_BYTES = 4 * 1024 * 1024;
    private static final int MAXIMUM_LOG_ARTIFACT_BYTES = 20 * 1024 * 1024;
    public static final int MAXIMUM_ARTIFACT_PREVIEW_BYTES = 1024 * 1024;
    private static final int MAXIMUM_LOG_PREVIEW_LINES = 2_000;

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

    private TaskRun requireRun(UUID id) {
        return runRepository.findById(id)
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

    private static <T> T requireTransactionResult(T result) {
        if (result == null) {
            throw new IllegalStateException("事务未返回预期结果");
        }
        return result;
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
}
