package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService.ExecutionRoute;
import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.business.task.domain.CanvasTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentActualState;
import cn.superhuang.data.scalpel.business.task.domain.StreamingSinkType;
import cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentExecutionMode;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingConfiguration;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingDeployment;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingQuery;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarTaskDefinition;
import cn.superhuang.data.scalpel.business.task.execution.service.TaskExecutionOutboxService;
import cn.superhuang.data.scalpel.business.task.repository.CanvasTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskStreamingConfigurationRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskStreamingDeploymentRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskStreamingQueryRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskDefinitionRepository;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateTaskStreamingConfigurationRequest;
import cn.superhuang.data.scalpel.business.task.web.response.TaskStreamingConfigurationResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskStreamingDeploymentResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskStreamingQueryResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskStreamingStatusResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunResponse;
import cn.superhuang.data.scalpel.contract.execution.ExecutionArtifactLocation;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.StartStreamingExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.StopStreamingExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionUserJarArtifact;
import cn.superhuang.data.scalpel.contract.execution.SparkStreamingJarExecutionPayload;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;
import cn.superhuang.data.scalpel.contract.execution.StreamingCheckpointMode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class TaskStreamingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TaskStreamingService.class);
    private static final List<StreamingDeploymentActualState> ACTIVE = List.of(
            StreamingDeploymentActualState.STARTING,
            StreamingDeploymentActualState.RUNNING,
            StreamingDeploymentActualState.STOPPING
    );
    private static final List<cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus> ACTIVE_RUNS = List.of(
            cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus.QUEUED,
            cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus.RUNNING,
            cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus.CANCEL_REQUESTED,
            cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus.STOP_REQUESTED
    );
    private static final long STREAMING_TRIAL_SECONDS = 30 * 60;

    private final DataTaskRepository taskRepository;
    private final CanvasTaskDefinitionRepository definitionRepository;
    private final TaskStreamingConfigurationRepository configurationRepository;
    private final TaskStreamingDeploymentRepository deploymentRepository;
    private final TaskStreamingQueryRepository queryRepository;
    private final TaskRunRepository runRepository;
    private final SparkJarTaskDefinitionRepository sparkJarDefinitionRepository;
    private final CanvasTaskDefinitionService definitionService;
    private final CanvasTaskRunPreparationService preparationService;
    private final SparkJarTaskDefinitionService sparkJarDefinitionService;
    private final SparkJarTaskRunPreparationService sparkJarPreparationService;
    private final ComputeEngineExecutionService computeEngineExecutionService;
    private final TaskExecutionOutboxService executionOutboxService;
    private final ObjectProvider<TaskRunArtifactStorage> artifactStorageProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readTransaction;
    private final TmqConsumerGroupCleanupService tmqCleanupService;

    public TaskStreamingService(
            DataTaskRepository taskRepository,
            CanvasTaskDefinitionRepository definitionRepository,
            TaskStreamingConfigurationRepository configurationRepository,
            TaskStreamingDeploymentRepository deploymentRepository,
            TaskStreamingQueryRepository queryRepository,
            TaskRunRepository runRepository,
            SparkJarTaskDefinitionRepository sparkJarDefinitionRepository,
            CanvasTaskDefinitionService definitionService,
            CanvasTaskRunPreparationService preparationService,
            SparkJarTaskDefinitionService sparkJarDefinitionService,
            SparkJarTaskRunPreparationService sparkJarPreparationService,
            ComputeEngineExecutionService computeEngineExecutionService,
            TaskExecutionOutboxService executionOutboxService,
            ObjectProvider<TaskRunArtifactStorage> artifactStorageProvider,
            ObjectMapper objectMapper,
            TmqConsumerGroupCleanupService tmqCleanupService,
            PlatformTransactionManager transactionManager
    ) {
        this.taskRepository = taskRepository;
        this.definitionRepository = definitionRepository;
        this.configurationRepository = configurationRepository;
        this.deploymentRepository = deploymentRepository;
        this.queryRepository = queryRepository;
        this.runRepository = runRepository;
        this.sparkJarDefinitionRepository = sparkJarDefinitionRepository;
        this.definitionService = definitionService;
        this.preparationService = preparationService;
        this.sparkJarDefinitionService = sparkJarDefinitionService;
        this.sparkJarPreparationService = sparkJarPreparationService;
        this.computeEngineExecutionService = computeEngineExecutionService;
        this.executionOutboxService = executionOutboxService;
        this.artifactStorageProvider = artifactStorageProvider;
        this.objectMapper = objectMapper;
        this.tmqCleanupService = tmqCleanupService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    @Transactional(readOnly = true)
    public TaskStreamingConfigurationResponse getConfiguration(UUID taskId) {
        requireStreamingCanvasTask(taskId);
        CanvasTaskDefinition persisted = definitionRepository.findByTaskId(taskId).orElse(null);
        if (persisted == null) {
            TaskStreamingConfiguration legacy = configurationRepository.findByTaskId(taskId).orElse(null);
            return legacy == null
                    ? TaskStreamingConfigurationResponse.fromDefinition(
                            taskId, KafkaInputConfiguration.DEFAULT_TRIGGER_INTERVAL_SECONDS, null, null)
                    : TaskStreamingConfigurationResponse.from(legacy);
        }
        CanvasDefinition definition = definitionService.requireReadable(persisted);
        return TaskStreamingConfigurationResponse.fromDefinition(
                taskId, definitionService.unboundedInputTriggerInterval(definition),
                persisted.getCreatedAt(), persisted.getUpdatedAt());
    }

    @Transactional
    public TaskStreamingConfigurationResponse updateConfiguration(
            UUID taskId,
            UpdateTaskStreamingConfigurationRequest request
    ) {
        requireStreamingCanvasTask(taskId);
        if (deploymentRepository.existsByTaskIdAndActualStateIn(taskId, ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "实时任务运行期间不能修改微批间隔，请先停止");
        }
        var updated = definitionService.updateUnboundedInputTriggerInterval(
                taskId, request.triggerIntervalSeconds());
        return TaskStreamingConfigurationResponse.fromDefinition(
                taskId, request.triggerIntervalSeconds(), updated.updatedAt(), updated.updatedAt());
    }

    @Transactional(readOnly = true)
    public TaskStreamingStatusResponse status(UUID taskId) {
        requireStreamingTask(taskId);
        TaskStreamingDeployment deployment = deploymentRepository
                .findFirstByTaskIdAndExecutionModeOrderByDefinitionVersionDescCheckpointGenerationDesc(
                        taskId, StreamingDeploymentExecutionMode.REAL).orElse(null);
        return status(taskId, deployment);
    }

    public TaskStreamingStatusResponse start(UUID taskId) {
        return start(taskId, null);
    }

    public TaskStreamingStatusResponse start(UUID taskId, StreamingCheckpointMode checkpointMode) {
        DataTask task = requireStreamingTask(taskId);
        if (task.getType() == TaskType.SPARK_STREAMING_JAR) {
            return startStreamingJar(taskId, checkpointMode);
        }
        StartSource source = requireStartSource(taskId);
        if (source.activeDeployment() != null) {
            return status(taskId, source.activeDeployment());
        }
        ExecutionRoute route = computeEngineExecutionService.requireStreamingRunnable(source.computeEngineId());
        CanvasTaskRunPreparationService.Preparation preparation = preparationService.prepare(
                source.definition(), CanvasExecutionMode.STREAMING);
        StreamingSourceIdentity sourceIdentity = streamingSourceIdentity(
                source.definition(), preparation);
        TaskStreamingDeployment deployment = requireTransactionResult(transaction.execute(status ->
                requireOrCreateDeployment(taskId, source, route, sourceIdentity)));
        int attempt = runRepository.findFirstByStreamingDeploymentIdOrderByAttemptDesc(deployment.getId())
                .map(TaskRun::getAttempt).orElse(0) + 1;
        UUID runId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        String base = "task-runs/%s/attempts/%d/".formatted(runId, attempt);
        String manifestKey = base + "manifest.json";
        String resultKey = base + "result.json";
        String logKey = base + "console.log";
        CanvasTaskRunManifest manifest = new CanvasTaskRunManifest(
                CanvasTaskRunManifest.CURRENT_MANIFEST_VERSION,
                new CanvasTaskRunManifest.Execution(
                        executionId, runId, taskId, attempt, source.definitionVersion(),
                        createdAt, null, deployment.getId()),
                new CanvasTaskRunManifest.Task(
                        cn.superhuang.data.scalpel.contract.task.TaskType.CANVAS,
                        source.definition(),
                        CanvasExecutionMode.STREAMING),
                preparation.metadataSnapshot(),
                preparation.runtimeDataSources(),
                new CanvasTaskRunManifest.Streaming(
                        definitionService.unboundedInputTriggerInterval(source.definition()),
                        deployment.getCheckpointKeyPrefix(),
                        deployment.getSourceNodeId() == null
                                ? null : deployment.getSourceNodeId().toString(),
                        deployment.getSourceSignature(),
                        deployment.getInitialSourceOffset())
        );
        byte[] manifestBytes = objectMapper.writeValueAsString(manifest).getBytes(StandardCharsets.UTF_8);
        String manifestSha256 = sha256(manifestBytes);
        TaskRunArtifactStorage storage = requireArtifactStorage();
        try {
            storage.store(manifestKey, manifestBytes, "application/json");
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务制品存储当前不可用", exception);
        }
        try {
            requireTransactionResult(transaction.execute(status -> queueStart(
                    taskId, source, deployment.getId(), route, preparation, runId, executionId, attempt,
                    manifestKey, manifestSha256, resultKey, logKey)));
            return status(taskId);
        } catch (RuntimeException exception) {
            try {
                storage.delete(manifestKey);
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    private TaskStreamingStatusResponse startStreamingJar(
            UUID taskId,
            StreamingCheckpointMode checkpointMode
    ) {
        JarStartSource source = requireJarStartSource(taskId);
        if (source.activeDeployment() != null) {
            return status(taskId, source.activeDeployment());
        }
        if (checkpointMode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Spark 实时 JAR 启动必须选择 CONTINUE 或 FRESH");
        }
        if (checkpointMode == StreamingCheckpointMode.CONTINUE && source.latestDeployment() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "当前任务没有可继续的 Checkpoint，请选择全新启动");
        }
        sparkJarDefinitionService.validatePublishable(taskId);
        sparkJarDefinitionService.validateArtifactAvailable(taskId);
        ExecutionRoute route = computeEngineExecutionService.requireStreamingRunnable(source.computeEngineId());
        SparkExecutionResourceSpec executionResources = computeEngineExecutionService.resolveResources(route,
                sparkJarDefinitionService.resolvedExecutionResources(taskId, source.definition()));
        TaskStreamingDeployment deployment = requireTransactionResult(transaction.execute(status ->
                requireOrCreateJarDeployment(taskId, source, route, checkpointMode)));
        if (deployment.getActualState().active()) return status(taskId, deployment);

        SparkJarTaskRunPreparationService.StreamingPreparation preparation =
                sparkJarPreparationService.prepareStreaming(source.definition(), deployment);
        int attempt = runRepository.findFirstByStreamingDeploymentIdOrderByAttemptDesc(deployment.getId())
                .map(TaskRun::getAttempt).orElse(0) + 1;
        UUID runId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        String base = "task-runs/%s/attempts/%d/".formatted(runId, attempt);
        String manifestKey = base + "manifest.json";
        String runJarKey = base + "user-job.jar";
        String resultKey = base + "result.json";
        String logKey = base + "console.log";
        CanvasTaskRunManifest manifest = new CanvasTaskRunManifest(
                CanvasTaskRunManifest.CURRENT_MANIFEST_VERSION,
                new CanvasTaskRunManifest.Execution(
                        executionId, runId, taskId, attempt, source.definitionVersion(),
                        createdAt, null, deployment.getId()),
                null, preparation.metadataSnapshot(), preparation.runtimeDataSources(),
                null, null, List.of(), CanvasTaskRunManifest.SnapshotSyncLimits.defaults(),
                ExecutionTaskType.SPARK_STREAMING_JAR, null, null, preparation.payload());
        byte[] manifestBytes = objectMapper.writeValueAsString(manifest).getBytes(StandardCharsets.UTF_8);
        String manifestSha256 = sha256(manifestBytes);
        TaskRunArtifactStorage storage = requireArtifactStorage();
        byte[] userJar;
        try {
            userJar = storage.readIfPresent(source.definition().getJarObjectKey(),
                            (int) SparkJarTaskDefinitionService.MAX_JAR_BYTES)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT, "当前用户 JAR 制品不存在，请重新上传"));
            if (userJar.length != source.definition().getJarSizeBytes()
                    || !sha256(userJar).equals(source.definition().getJarSha256())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "当前用户 JAR 制品摘要不一致，请重新上传");
            }
            storage.store(runJarKey, userJar, "application/java-archive");
            storage.store(manifestKey, manifestBytes, "application/json");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "任务制品存储当前不可用", exception);
        }
        try {
            requireTransactionResult(transaction.execute(status -> queueJarStart(
                    taskId, source, deployment.getId(), route, preparation,
                    runId, executionId, attempt, manifestKey, manifestSha256,
                    runJarKey, resultKey, logKey, executionResources)));
            return status(taskId);
        } catch (RuntimeException exception) {
            try { storage.delete(manifestKey); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            try { storage.delete(runJarKey); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }
    }

    public TaskRunResponse submitStreamingJarTrial(
            UUID taskId,
            String sourceSha256,
            byte[] userJar,
            String jarSha256
    ) {
        if (sourceSha256 == null || sourceSha256.isBlank() || userJar == null || userJar.length == 0
                || !Objects.equals(jarSha256, sha256(userJar))) {
            throw new IllegalArgumentException("实时试运行编译制品无效");
        }
        SparkJarTaskDefinition definition = requireTransactionResult(readTransaction.execute(status -> {
            DataTask task = requireStreamingTask(taskId);
            if (task.getType() != TaskType.SPARK_STREAMING_JAR
                    || task.getStatus() != TaskStatus.DRAFT && task.getStatus() != TaskStatus.DISABLED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务状态不允许实时在线试运行");
            }
            if (runRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE_RUNS)
                    || deploymentRepository.existsByTaskIdAndActualStateIn(taskId, ACTIVE)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务已有正在执行的正式运行或试运行");
            }
            SparkJarTaskDefinition current = sparkJarDefinitionRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "Spark 实时 JAR 定义不存在"));
            String savedSource = current.getOnlineSourceCode();
            if (savedSource == null || !sourceSha256.equals(
                    sha256(savedSource.getBytes(StandardCharsets.UTF_8)))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "在线源码已变化，请重新试运行");
            }
            return current;
        }));
        ExecutionRoute route = computeEngineExecutionService.requireStreamingRunnable(
                requireStreamingTask(taskId).getComputeEngineId());
        SparkExecutionResourceSpec resources = computeEngineExecutionService.resolveResources(
                route, sparkJarDefinitionService.resolvedExecutionResources(taskId, definition));
        UUID runId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant deadlineAt = createdAt.plusSeconds(STREAMING_TRIAL_SECONDS).truncatedTo(ChronoUnit.SECONDS);
        TaskStreamingDeployment deployment = requireTransactionResult(transaction.execute(status -> {
            DataTask task = taskRepository.findByIdForUpdate(taskId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
            if (task.getType() != TaskType.SPARK_STREAMING_JAR
                    || task.getStatus() != TaskStatus.DRAFT && task.getStatus() != TaskStatus.DISABLED
                    || runRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE_RUNS)
                    || deploymentRepository.existsByTaskIdAndActualStateIn(taskId, ACTIVE)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务状态已变化，请重新试运行");
            }
            int generation = deploymentRepository
                    .findFirstByTaskIdAndDefinitionVersionOrderByCheckpointGenerationDesc(
                            taskId, definition.getVersion())
                    .map(value -> value.getCheckpointGeneration() + 1).orElse(1);
            return deploymentRepository.saveAndFlush(TaskStreamingDeployment.createTrial(
                    taskId, definition.getVersion(), route.engineId(),
                    "streaming-jar-trials/%s/%s".formatted(taskId, runId), generation));
        }));
        SparkJarTaskRunPreparationService.StreamingPreparation preparation =
                sparkJarPreparationService.prepareStreaming(definition, deployment);
        String base = "task-runs/%s/attempts/1/".formatted(runId);
        String manifestKey = base + "manifest.json";
        String runJarKey = base + "user-job.jar";
        String resultKey = base + "result.json";
        String logKey = base + "console.log";
        CanvasTaskRunManifest manifest = new CanvasTaskRunManifest(
                CanvasTaskRunManifest.CURRENT_MANIFEST_VERSION,
                new CanvasTaskRunManifest.Execution(
                        executionId, runId, taskId, 1, definition.getVersion(),
                        createdAt, null, deployment.getId()),
                null, preparation.metadataSnapshot(), preparation.runtimeDataSources(),
                null, null, List.of(), CanvasTaskRunManifest.SnapshotSyncLimits.defaults(),
                ExecutionTaskType.SPARK_STREAMING_JAR, null, null, preparation.payload());
        byte[] manifestBytes = objectMapper.writeValueAsString(manifest).getBytes(StandardCharsets.UTF_8);
        String manifestSha256 = sha256(manifestBytes);
        TaskRunArtifactStorage storage = requireArtifactStorage();
        try {
            storage.store(runJarKey, userJar, "application/java-archive");
            storage.store(manifestKey, manifestBytes, "application/json");
        } catch (RuntimeException exception) {
            try { storage.delete(runJarKey); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            try { storage.delete(manifestKey); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "任务制品存储当前不可用", exception);
        }
        String fileName = "online-streaming-trial-" + sourceSha256.substring(0, 12) + ".jar";
        String snapshot = objectMapper.writeValueAsString(Map.of(
                "manifestVersion", CanvasTaskRunManifest.CURRENT_MANIFEST_VERSION,
                "deploymentId", deployment.getId().toString(),
                "executionMode", "TRIAL",
                "jarFileName", fileName,
                "jarSha256", jarSha256,
                "jobClass", definition.getJobClass(),
                "jobApiVersion", definition.getJobApiVersion(),
                "checkpointMode", "FRESH",
                "executionResources", resources));
        try {
            TaskRun saved = requireTransactionResult(transaction.execute(status -> {
                DataTask task = taskRepository.findByIdForUpdate(taskId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
                TaskStreamingDeployment locked = deploymentRepository.findByIdForUpdate(deployment.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "实时试运行部署不存在"));
                SparkJarTaskDefinition current = sparkJarDefinitionRepository.findByTaskId(taskId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                                "Spark 实时 JAR 定义不存在"));
                if (task.getType() != TaskType.SPARK_STREAMING_JAR
                        || task.getStatus() != TaskStatus.DRAFT && task.getStatus() != TaskStatus.DISABLED
                        || current.getVersion() != definition.getVersion()
                        || current.getOnlineSourceCode() == null
                        || !sourceSha256.equals(sha256(current.getOnlineSourceCode()
                        .getBytes(StandardCharsets.UTF_8)))
                        || runRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE_RUNS)
                        || locked.getActualState().active()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "任务定义或在线源码已变化，请重新试运行");
                }
                computeEngineExecutionService.assertUnchanged(route);
                sparkJarPreparationService.assertUnchanged(preparation);
                TaskRun run = TaskRun.queueDispatchedStreamingTrial(
                        runId, taskId, locked.getId(), current.getVersion(), snapshot,
                        executionId, 1, deadlineAt, route.engineId(), route.commandTopic());
                run.attachUserJar(fileName, jarSha256, userJar.length, runJarKey);
                run.attachArtifacts(manifestKey, resultKey, logKey);
                TaskRun queued = runRepository.saveAndFlush(run);
                locked.beginStart(queued.getId());
                deploymentRepository.save(locked);
                executionOutboxService.enqueue(route.commandTopic(), new StartStreamingExecutionCommand(
                        1, UUID.randomUUID(), ExecutionMessageType.START_STREAMING_EXECUTION, Instant.now(),
                        route.engineId(), executionId, runId, 1, taskId, locked.getId(),
                        current.getVersion(), locked.getCheckpointKeyPrefix(),
                        ExecutionTaskType.SPARK_STREAMING_JAR,
                        new ExecutionArtifactLocation(manifestKey, manifestSha256, resultKey, logKey),
                        new ExecutionUserJarArtifact(runJarKey, jarSha256, userJar.length),
                        preparation.payload().sparkConf(), resources));
                return queued;
            }));
            return TaskRunResponse.from(saved);
        } catch (RuntimeException exception) {
            try { storage.delete(manifestKey); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            try { storage.delete(runJarKey); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }
    }

    private JarStartSource requireJarStartSource(UUID taskId) {
        return requireTransactionResult(readTransaction.execute(status -> {
            DataTask task = requireStreamingTask(taskId);
            if (task.getType() != TaskType.SPARK_STREAMING_JAR || task.getStatus() != TaskStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "只有已发布 Spark 实时 JAR 任务可以启动");
            }
            if (deploymentRepository.existsByTaskIdAndExecutionModeAndActualStateIn(
                    taskId, StreamingDeploymentExecutionMode.TRIAL, ACTIVE)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "当前任务存在活动的实时在线试运行，请先停止试运行");
            }
            SparkJarTaskDefinition definition = sparkJarDefinitionRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "Spark 实时 JAR 定义不存在"));
            TaskStreamingDeployment latest = deploymentRepository
                    .findFirstByTaskIdAndExecutionModeOrderByDefinitionVersionDescCheckpointGenerationDesc(
                            taskId, StreamingDeploymentExecutionMode.REAL)
                    .orElse(null);
            return new JarStartSource(task.getComputeEngineId(), definition.getVersion(), definition,
                    latest, latest != null && latest.getActualState().active() ? latest : null);
        }));
    }

    private TaskStreamingDeployment requireOrCreateJarDeployment(
            UUID taskId,
            JarStartSource source,
            ExecutionRoute route,
            StreamingCheckpointMode checkpointMode
    ) {
        DataTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (task.getType() != TaskType.SPARK_STREAMING_JAR || task.getStatus() != TaskStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "实时 JAR 任务状态已变化");
        }
        if (deploymentRepository.existsByTaskIdAndExecutionModeAndActualStateIn(
                taskId, StreamingDeploymentExecutionMode.TRIAL, ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "当前任务存在活动的实时在线试运行，请先停止试运行");
        }
        SparkJarTaskDefinition currentDefinition = sparkJarDefinitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Spark 实时 JAR 定义不存在"));
        if (currentDefinition.getVersion() != source.definitionVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark 实时 JAR 定义已变化，请重新启动");
        }
        TaskStreamingDeployment latest = deploymentRepository
                .findFirstByTaskIdAndExecutionModeOrderByDefinitionVersionDescCheckpointGenerationDesc(
                        taskId, StreamingDeploymentExecutionMode.REAL)
                .orElse(null);
        if (latest != null && latest.getActualState().active()) return latest;
        if (checkpointMode == StreamingCheckpointMode.CONTINUE && latest == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务没有可继续的 Checkpoint");
        }
        if (checkpointMode == StreamingCheckpointMode.CONTINUE
                && latest.getDefinitionVersion() == source.definitionVersion()) {
            return deploymentRepository.findByIdForUpdate(latest.getId()).orElseThrow();
        }
        int generation = deploymentRepository
                .findFirstByTaskIdAndDefinitionVersionOrderByCheckpointGenerationDesc(
                        taskId, source.definitionVersion())
                .map(value -> value.getCheckpointGeneration() + 1).orElse(1);
        UUID sourceDeploymentId = checkpointMode == StreamingCheckpointMode.CONTINUE ? latest.getId() : null;
        String checkpointPrefix = checkpointMode == StreamingCheckpointMode.CONTINUE
                ? latest.getCheckpointKeyPrefix()
                : "streaming-jar/%s/definitions/%d/generations/%d".formatted(
                        taskId, source.definitionVersion(), generation);
        return deploymentRepository.saveAndFlush(TaskStreamingDeployment.create(
                taskId, source.definitionVersion(), route.engineId(), checkpointPrefix,
                generation, checkpointMode, sourceDeploymentId));
    }

    private TaskRun queueJarStart(
            UUID taskId,
            JarStartSource source,
            UUID deploymentId,
            ExecutionRoute route,
            SparkJarTaskRunPreparationService.StreamingPreparation preparation,
            UUID runId,
            UUID executionId,
            int attempt,
            String manifestKey,
            String manifestSha256,
            String runJarKey,
            String resultKey,
            String logKey,
            SparkExecutionResourceSpec executionResources
    ) {
        DataTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        TaskStreamingDeployment deployment = deploymentRepository.findByIdForUpdate(deploymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "实时部署不存在"));
        if (task.getType() != TaskType.SPARK_STREAMING_JAR || task.getStatus() != TaskStatus.PUBLISHED
                || deployment.getActualState().active()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "实时 JAR 任务状态已变化，请重新启动");
        }
        SparkJarTaskDefinition definition = sparkJarDefinitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Spark 实时 JAR 定义不存在"));
        if (definition.getVersion() != source.definitionVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark 实时 JAR 定义已变化，请重新启动");
        }
        computeEngineExecutionService.assertUnchanged(route);
        sparkJarPreparationService.assertUnchanged(preparation);
        String snapshot = objectMapper.writeValueAsString(Map.of(
                "manifestVersion", CanvasTaskRunManifest.CURRENT_MANIFEST_VERSION,
                "deploymentId", deploymentId.toString(),
                "jarFileName", definition.getJarFileName(),
                "jarSha256", definition.getJarSha256(),
                "jobClass", definition.getJobClass(),
                "jobApiVersion", definition.getJobApiVersion(),
                "checkpointMode", preparation.payload().checkpointMode().name(),
                "executionResources", executionResources));
        TaskRun run = TaskRun.queueDispatchedStreaming(
                runId, taskId, deploymentId, source.definitionVersion(), snapshot,
                executionId, attempt, route.engineId(), route.commandTopic(),
                TaskType.SPARK_STREAMING_JAR);
        run.attachArtifacts(manifestKey, resultKey, logKey);
        run.attachUserJar(definition.getJarFileName(), definition.getJarSha256(),
                definition.getJarSizeBytes(), runJarKey);
        TaskRun saved = runRepository.saveAndFlush(run);
        deployment.beginStart(saved.getId());
        deploymentRepository.save(deployment);
        queryRepository.findAllByDeploymentIdOrderByOutputNodeNameAsc(deploymentId)
                .forEach(TaskStreamingQuery::beginStart);
        executionOutboxService.enqueue(route.commandTopic(), new StartStreamingExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.START_STREAMING_EXECUTION, Instant.now(),
                route.engineId(), executionId, runId, attempt, taskId, deploymentId,
                source.definitionVersion(), deployment.getCheckpointKeyPrefix(),
                ExecutionTaskType.SPARK_STREAMING_JAR,
                new ExecutionArtifactLocation(manifestKey, manifestSha256, resultKey, logKey),
                new ExecutionUserJarArtifact(runJarKey, definition.getJarSha256(),
                        definition.getJarSizeBytes()),
                preparation.payload().sparkConf(), executionResources));
        return saved;
    }

    public TaskStreamingStatusResponse stop(UUID taskId) {
        requireTransactionResult(transaction.execute(status -> {
            DataTask task = requireStreamingTaskForUpdate(taskId);
            TaskStreamingDeployment deployment = deploymentRepository
                    .findFirstByTaskIdAndExecutionModeOrderByDefinitionVersionDescCheckpointGenerationDesc(
                            taskId, StreamingDeploymentExecutionMode.REAL)
                    .flatMap(item -> deploymentRepository.findByIdForUpdate(item.getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "实时任务从未启动"));
            if (deployment.getActualState() == StreamingDeploymentActualState.STOPPED
                    || deployment.getActualState() == StreamingDeploymentActualState.STOPPING) {
                return deployment;
            }
            if (!deployment.getActualState().active()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前实时部署不在运行状态");
            }
            TaskRun run = deployment.getCurrentRunId() == null ? null
                    : runRepository.findByIdForUpdate(deployment.getCurrentRunId()).orElse(null);
            if (run == null || run.getExternalExecutionId() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "实时部署缺少当前运行实例");
            }
            deployment.requestStop();
            run.requestStop();
            queryRepository.findAllByDeploymentIdOrderByOutputNodeNameAsc(deployment.getId())
                    .forEach(TaskStreamingQuery::requestStop);
            runRepository.save(run);
            deploymentRepository.save(deployment);
            executionOutboxService.enqueue(run.getCommandTopicSnapshot(), new StopStreamingExecutionCommand(
                    1, UUID.randomUUID(), ExecutionMessageType.STOP_STREAMING_EXECUTION, Instant.now(),
                    task.getComputeEngineId(), run.getExternalExecutionId(), run.getExecutionRunId(), run.getAttempt(),
                    deployment.getId(), "用户请求停止实时任务", 60
            ));
            return deployment;
        }));
        return status(taskId);
    }

    public TaskRunResponse stopTrial(UUID runId) {
        return requireTransactionResult(transaction.execute(status -> {
            TaskRun run = runRepository.findByIdForUpdate(runId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务运行不存在"));
            if (run.getTaskType() != TaskType.SPARK_STREAMING_JAR
                    || run.getExecutionMode()
                    != cn.superhuang.data.scalpel.business.task.domain.TaskRunExecutionMode.TRIAL) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "只有 Spark 实时 JAR 在线试运行支持正常停止");
            }
            if (run.getStatus() == cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus.STOP_REQUESTED
                    || run.getStatus() == cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus.STOPPED) {
                return TaskRunResponse.from(run);
            }
            if (!ACTIVE_RUNS.contains(run.getStatus()) || run.getStreamingDeploymentId() == null
                    || run.getExternalExecutionId() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前实时试运行已经结束");
            }
            TaskStreamingDeployment deployment = deploymentRepository
                    .findByIdForUpdate(run.getStreamingDeploymentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "实时试运行部署不存在"));
            if (deployment.getExecutionMode() != StreamingDeploymentExecutionMode.TRIAL) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "实时试运行部署模式无效");
            }
            deployment.requestStop();
            run.requestStop();
            runRepository.save(run);
            deploymentRepository.save(deployment);
            executionOutboxService.enqueue(run.getCommandTopicSnapshot(), new StopStreamingExecutionCommand(
                    1, UUID.randomUUID(), ExecutionMessageType.STOP_STREAMING_EXECUTION, Instant.now(),
                    run.getComputeEngineId(), run.getExternalExecutionId(), run.getExecutionRunId(),
                    run.getAttempt(), deployment.getId(), "停止实时在线试运行", 60));
            return TaskRunResponse.from(run);
        }));
    }

    @Scheduled(fixedDelay = 5_000L)
    public void stopExpiredStreamingJarTrials() {
        List<TaskRun> expired = runRepository
                .findAllByTaskTypeAndExecutionModeAndStatusInAndDeadlineAtLessThanEqual(
                        TaskType.SPARK_STREAMING_JAR,
                        cn.superhuang.data.scalpel.business.task.domain.TaskRunExecutionMode.TRIAL,
                        List.of(cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus.QUEUED,
                                cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus.RUNNING),
                        Instant.now());
        for (TaskRun run : expired) {
            try {
                stopTrial(run.getId());
            } catch (RuntimeException exception) {
                log.warn("Expired streaming JAR trial stop failed: runId={}", run.getId(), exception);
            }
        }
    }

    private StartSource requireStartSource(UUID taskId) {
        return requireTransactionResult(readTransaction.execute(status -> {
            DataTask task = requireStreamingTask(taskId);
            if (task.getStatus() != TaskStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布实时任务可以启动");
            }
            CanvasTaskDefinition persisted = definitionRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "实时 Canvas 定义不存在"));
            TaskStreamingDeployment active = deploymentRepository
                    .findFirstByTaskIdAndDefinitionVersionAndExecutionModeOrderByCheckpointGenerationDesc(
                            taskId, persisted.getVersion(), StreamingDeploymentExecutionMode.REAL)
                    .filter(item -> item.getActualState().active())
                    .orElse(null);
            return new StartSource(
                    task.getComputeEngineId(),
                    persisted.getVersion(),
                    definitionService.requireReadable(persisted),
                    active
            );
        }));
    }

    private TaskStreamingDeployment requireOrCreateDeployment(
            UUID taskId,
            StartSource source,
            ExecutionRoute route,
            StreamingSourceIdentity sourceIdentity
    ) {
        DataTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (task.getType() != TaskType.SPARK_STREAMING_CANVAS || task.getStatus() != TaskStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "实时任务状态已变化");
        }
        TaskStreamingDeployment deployment = deploymentRepository
                .findFirstByTaskIdAndDefinitionVersionAndExecutionModeOrderByCheckpointGenerationDesc(
                        taskId, source.definitionVersion(), StreamingDeploymentExecutionMode.REAL)
                .flatMap(value -> deploymentRepository.findByIdForUpdate(value.getId())).orElse(null);
        String initialSourceOffset = sourceIdentity == null ? null : deploymentRepository
                .findFirstByTaskIdAndDefinitionVersionLessThanAndExecutionModeOrderByDefinitionVersionDescCheckpointGenerationDesc(
                        taskId, source.definitionVersion(), StreamingDeploymentExecutionMode.REAL)
                .filter(previous -> sourceIdentity.signature().equals(previous.getSourceSignature()))
                .map(TaskStreamingDeployment::getLastCommittedOffset)
                .orElse(null);
        if (deployment == null) {
            deployment = deploymentRepository.saveAndFlush(TaskStreamingDeployment.create(
                    taskId,
                    source.definitionVersion(),
                    route.engineId(),
                    "streaming/%s/definitions/%d".formatted(taskId, source.definitionVersion()),
                    sourceIdentity == null ? null : sourceIdentity.nodeId(),
                    sourceIdentity == null ? null : sourceIdentity.signature(),
                    initialSourceOffset
            ));
            List<TaskStreamingQuery> queries = outputQueries(source.definition(), deployment);
            queryRepository.saveAllAndFlush(queries);
        } else if (sourceIdentity != null) {
            if (deployment.getSourceSignature() == null) {
                deployment.initializeSource(
                        sourceIdentity.nodeId(), sourceIdentity.signature(), initialSourceOffset);
                deploymentRepository.saveAndFlush(deployment);
            } else if (!sourceIdentity.nodeId().equals(deployment.getSourceNodeId())
                    || !sourceIdentity.signature().equals(deployment.getSourceSignature())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "当前定义的实时来源与已有同版本部署不一致，请保存为新的定义版本"
                );
            }
        }
        return deployment;
    }

    private static StreamingSourceIdentity streamingSourceIdentity(
            CanvasDefinition definition,
            CanvasTaskRunPreparationService.Preparation preparation
    ) {
        TdEngineTmqInputNodeDefinition tmqNode = definition.nodes().stream()
                .filter(TdEngineTmqInputNodeDefinition.class::isInstance)
                .map(TdEngineTmqInputNodeDefinition.class::cast)
                .findFirst().orElse(null);
        if (tmqNode != null) {
            UUID dataSourceId = UUID.fromString(tmqNode.configuration().dataSourceId());
            MetadataDataSource metadataDataSource = preparation.metadataSnapshot().dataSources().stream()
                    .filter(value -> dataSourceId.equals(value.id()))
                    .findFirst().orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT, "TDengine TMQ 数据源元数据不存在"));
            MetadataTdEngineTmqTopic topic = metadataDataSource.tdEngineTmqTopics().stream()
                    .filter(value -> tmqNode.configuration().topicName().equals(value.topicName()))
                    .findFirst().orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT, "TDengine TMQ Topic 元数据不存在"));
            String signatureSource = dataSourceId + "\0" + topic.topicName() + "\0"
                    + topic.definitionFingerprint();
            return new StreamingSourceIdentity(
                    UUID.fromString(tmqNode.id()),
                    sha256(signatureSource.getBytes(StandardCharsets.UTF_8))
            );
        }
        JdbcIncrementalInputNodeDefinition node = definition.nodes().stream()
                .filter(JdbcIncrementalInputNodeDefinition.class::isInstance)
                .map(JdbcIncrementalInputNodeDefinition.class::cast)
                .findFirst().orElse(null);
        if (node == null) return null;
        UUID dataSourceId = UUID.fromString(node.configuration().dataSourceId());
        MetadataDataSource metadataDataSource = preparation.metadataSnapshot().dataSources().stream()
                .filter(value -> dataSourceId.equals(value.id()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "JDBC 增量数据源元数据不存在"));
        MetadataTable table = metadataDataSource.tables().stream()
                .filter(value -> node.configuration().tableName().equals(value.tableName()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "JDBC 增量物理表元数据不存在"));
        CanvasTaskRunManifest.RuntimeDataSource runtimeDataSource = preparation.runtimeDataSources().stream()
                .filter(value -> dataSourceId.equals(value.dataSourceId()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "JDBC 增量运行数据源不存在"));
        CanvasTaskRunManifest.RuntimeJdbcConnection connection = runtimeDataSource.connection();
        if (connection == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "JDBC 增量运行连接不存在");
        }
        return new StreamingSourceIdentity(
                UUID.fromString(node.id()),
                JdbcIncrementalSourceSignature.sha256(
                        node, connection.catalogName(), connection.schemaName(), table.columns())
        );
    }

    private TaskRun queueStart(
            UUID taskId,
            StartSource source,
            UUID deploymentId,
            ExecutionRoute route,
            CanvasTaskRunPreparationService.Preparation preparation,
            UUID runId,
            UUID executionId,
            int attempt,
            String manifestKey,
            String manifestSha256,
            String resultKey,
            String logKey
    ) {
        DataTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        TaskStreamingDeployment deployment = deploymentRepository.findByIdForUpdate(deploymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "实时部署不存在"));
        if (task.getStatus() != TaskStatus.PUBLISHED || task.getType() != TaskType.SPARK_STREAMING_CANVAS
                || deployment.getActualState().active()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "实时任务状态已变化，请重新启动");
        }
        CanvasTaskDefinition definition = definitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "实时 Canvas 定义不存在"));
        if (definition.getVersion() != source.definitionVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "实时 Canvas 定义已变化，请重新启动");
        }
        computeEngineExecutionService.assertUnchanged(route);
        preparationService.assertDataSourcesUnchanged(preparation.dataSourceVersions());
        preparationService.assertModelsUnchanged(preparation.modelVersions());
        String snapshot = objectMapper.writeValueAsString(Map.of(
                "manifestVersion", CanvasTaskRunManifest.CURRENT_MANIFEST_VERSION,
                "deploymentId", deploymentId.toString(),
                "manifestKey", manifestKey,
                "manifestSha256", manifestSha256
        ));
        TaskRun run = TaskRun.queueDispatchedStreaming(
                runId, taskId, deploymentId, source.definitionVersion(), snapshot,
                executionId, attempt, route.engineId(), route.commandTopic());
        run.attachArtifacts(manifestKey, resultKey, logKey);
        TaskRun saved = runRepository.saveAndFlush(run);
        deployment.beginStart(saved.getId());
        deploymentRepository.save(deployment);
        queryRepository.findAllByDeploymentIdOrderByOutputNodeNameAsc(deploymentId)
                .forEach(TaskStreamingQuery::beginStart);
        executionOutboxService.enqueue(route.commandTopic(), new StartStreamingExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.START_STREAMING_EXECUTION, Instant.now(),
                route.engineId(), executionId, runId, attempt, taskId, deploymentId,
                source.definitionVersion(),
                new ExecutionArtifactLocation(manifestKey, manifestSha256, resultKey, logKey)
        ));
        return saved;
    }

    private static List<TaskStreamingQuery> outputQueries(
            CanvasDefinition definition,
            TaskStreamingDeployment deployment
    ) {
        return definition.nodes().stream().flatMap(node -> outputWrites(node, deployment)).toList();
    }

    private static Stream<TaskStreamingQuery> outputWrites(
            CanvasNodeDefinition node,
            TaskStreamingDeployment deployment
    ) {
        UUID nodeId = UUID.fromString(node.id());
        String checkpointPrefix = deployment.getCheckpointKeyPrefix() + "/outputs/" + node.id() + "/";
        if (node instanceof KafkaOutputNodeDefinition output) {
            return output.configuration().writes().stream().map(write -> TaskStreamingQuery.create(
                    deployment.getId(), nodeId, UUID.fromString(write.writeId()), node.name(),
                    StreamingSinkType.KAFKA, checkpointPrefix + write.writeId()));
        }
        if (node instanceof JdbcOutputNodeDefinition output) {
            return output.configuration().writes().stream().map(write -> TaskStreamingQuery.create(
                    deployment.getId(), nodeId, UUID.fromString(write.writeId()), node.name(),
                    StreamingSinkType.JDBC, checkpointPrefix + write.writeId()));
        }
        if (node instanceof ModelOutputNodeDefinition output) {
            return output.configuration().writes().stream().map(write -> TaskStreamingQuery.create(
                    deployment.getId(), nodeId, UUID.fromString(write.writeId()), node.name(),
                    StreamingSinkType.JDBC, checkpointPrefix + write.writeId()));
        }
        return Stream.empty();
    }

    private TaskStreamingStatusResponse status(UUID taskId, TaskStreamingDeployment deployment) {
        TmqConsumerGroupCleanupService.CleanupCounts cleanupCounts = tmqCleanupService.counts(taskId);
        if (deployment == null) return new TaskStreamingStatusResponse(
                taskId, null, cleanupCounts.pending(), cleanupCounts.failed());
        TaskRun run = deployment.getCurrentRunId() == null
                ? null : runRepository.findById(deployment.getCurrentRunId()).orElse(null);
        List<TaskStreamingQueryResponse> queries = queryRepository
                .findAllByDeploymentIdOrderByOutputNodeNameAsc(deployment.getId())
                .stream().map(TaskStreamingQueryResponse::from).toList();
        return new TaskStreamingStatusResponse(
                taskId, TaskStreamingDeploymentResponse.from(deployment, run, queries),
                cleanupCounts.pending(), cleanupCounts.failed());
    }

    private DataTask requireStreamingTask(UUID taskId) {
        DataTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!task.getType().isStreaming()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不是 Spark 实时任务");
        }
        return task;
    }

    private DataTask requireStreamingCanvasTask(UUID taskId) {
        DataTask task = requireStreamingTask(taskId);
        if (task.getType() != TaskType.SPARK_STREAMING_CANVAS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不是 Spark 实时编排任务");
        }
        return task;
    }

    private DataTask requireStreamingTaskForUpdate(UUID taskId) {
        DataTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!task.getType().isStreaming()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不是 Spark 实时任务");
        }
        return task;
    }

    private TaskStreamingConfiguration requireConfiguration(UUID taskId) {
        return configurationRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalStateException("实时任务缺少默认配置"));
    }

    private TaskRunArtifactStorage requireArtifactStorage() {
        TaskRunArtifactStorage storage = artifactStorageProvider.getIfAvailable();
        if (storage == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务制品存储尚未配置");
        }
        return storage;
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算实时 Manifest 摘要", exception);
        }
    }

    private static <T> T requireTransactionResult(T result) {
        if (result == null) throw new IllegalStateException("事务未返回预期结果");
        return result;
    }

    private record StartSource(
            UUID computeEngineId,
            int definitionVersion,
            CanvasDefinition definition,
            TaskStreamingDeployment activeDeployment
    ) {
    }

    private record JarStartSource(
            UUID computeEngineId,
            int definitionVersion,
            SparkJarTaskDefinition definition,
            TaskStreamingDeployment latestDeployment,
            TaskStreamingDeployment activeDeployment
    ) {
    }

    private record StreamingSourceIdentity(UUID nodeId, String signature) {
    }
}
