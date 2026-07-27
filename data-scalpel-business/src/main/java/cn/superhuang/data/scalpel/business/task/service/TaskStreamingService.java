package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService.ExecutionRoute;
import cn.superhuang.data.scalpel.business.task.canvas.CanvasDefinition;
import cn.superhuang.data.scalpel.business.task.domain.CanvasTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentActualState;
import cn.superhuang.data.scalpel.business.task.domain.StreamingSinkType;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingConfiguration;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingDeployment;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingQuery;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.execution.service.TaskExecutionOutboxService;
import cn.superhuang.data.scalpel.business.task.repository.CanvasTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskStreamingConfigurationRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskStreamingDeploymentRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskStreamingQueryRepository;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateTaskStreamingConfigurationRequest;
import cn.superhuang.data.scalpel.business.task.web.response.TaskStreamingConfigurationResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskStreamingDeploymentResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskStreamingQueryResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskStreamingStatusResponse;
import cn.superhuang.data.scalpel.contract.execution.ExecutionArtifactLocation;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.StartStreamingExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.StopStreamingExecutionCommand;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskStreamingService {
    private static final List<StreamingDeploymentActualState> ACTIVE = List.of(
            StreamingDeploymentActualState.STARTING,
            StreamingDeploymentActualState.RUNNING,
            StreamingDeploymentActualState.STOPPING
    );

    private final DataTaskRepository taskRepository;
    private final CanvasTaskDefinitionRepository definitionRepository;
    private final TaskStreamingConfigurationRepository configurationRepository;
    private final TaskStreamingDeploymentRepository deploymentRepository;
    private final TaskStreamingQueryRepository queryRepository;
    private final TaskRunRepository runRepository;
    private final CanvasTaskDefinitionService definitionService;
    private final CanvasTaskRunPreparationService preparationService;
    private final ComputeEngineExecutionService computeEngineExecutionService;
    private final TaskExecutionOutboxService executionOutboxService;
    private final ObjectProvider<TaskRunArtifactStorage> artifactStorageProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readTransaction;

    public TaskStreamingService(
            DataTaskRepository taskRepository,
            CanvasTaskDefinitionRepository definitionRepository,
            TaskStreamingConfigurationRepository configurationRepository,
            TaskStreamingDeploymentRepository deploymentRepository,
            TaskStreamingQueryRepository queryRepository,
            TaskRunRepository runRepository,
            CanvasTaskDefinitionService definitionService,
            CanvasTaskRunPreparationService preparationService,
            ComputeEngineExecutionService computeEngineExecutionService,
            TaskExecutionOutboxService executionOutboxService,
            ObjectProvider<TaskRunArtifactStorage> artifactStorageProvider,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.taskRepository = taskRepository;
        this.definitionRepository = definitionRepository;
        this.configurationRepository = configurationRepository;
        this.deploymentRepository = deploymentRepository;
        this.queryRepository = queryRepository;
        this.runRepository = runRepository;
        this.definitionService = definitionService;
        this.preparationService = preparationService;
        this.computeEngineExecutionService = computeEngineExecutionService;
        this.executionOutboxService = executionOutboxService;
        this.artifactStorageProvider = artifactStorageProvider;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    @Transactional(readOnly = true)
    public TaskStreamingConfigurationResponse getConfiguration(UUID taskId) {
        requireStreamingTask(taskId);
        return TaskStreamingConfigurationResponse.from(requireConfiguration(taskId));
    }

    @Transactional
    public TaskStreamingConfigurationResponse updateConfiguration(
            UUID taskId,
            UpdateTaskStreamingConfigurationRequest request
    ) {
        requireStreamingTask(taskId);
        if (deploymentRepository.existsByTaskIdAndActualStateIn(taskId, ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "实时任务运行期间不能修改微批间隔，请先停止");
        }
        TaskStreamingConfiguration configuration = requireConfiguration(taskId);
        configuration.update(request.triggerIntervalSeconds());
        return TaskStreamingConfigurationResponse.from(configurationRepository.saveAndFlush(configuration));
    }

    @Transactional(readOnly = true)
    public TaskStreamingStatusResponse status(UUID taskId) {
        requireStreamingTask(taskId);
        TaskStreamingDeployment deployment = deploymentRepository
                .findFirstByTaskIdOrderByDefinitionVersionDesc(taskId).orElse(null);
        return status(taskId, deployment);
    }

    public TaskStreamingStatusResponse start(UUID taskId) {
        StartSource source = requireStartSource(taskId);
        if (source.activeDeployment() != null) {
            return status(taskId, source.activeDeployment());
        }
        ExecutionRoute route = computeEngineExecutionService.requireStreamingRunnable(source.computeEngineId());
        CanvasTaskRunPreparationService.Preparation preparation = preparationService.prepare(
                source.definition(), CanvasExecutionMode.STREAMING);
        TaskStreamingDeployment deployment = requireTransactionResult(transaction.execute(status ->
                requireOrCreateDeployment(taskId, source, route)));
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
                        source.triggerIntervalSeconds(), deployment.getCheckpointKeyPrefix())
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

    public TaskStreamingStatusResponse stop(UUID taskId) {
        requireTransactionResult(transaction.execute(status -> {
            DataTask task = requireStreamingTaskForUpdate(taskId);
            TaskStreamingDeployment deployment = deploymentRepository
                    .findFirstByTaskIdOrderByDefinitionVersionDesc(taskId)
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

    private StartSource requireStartSource(UUID taskId) {
        return requireTransactionResult(readTransaction.execute(status -> {
            DataTask task = requireStreamingTask(taskId);
            if (task.getStatus() != TaskStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布实时任务可以启动");
            }
            CanvasTaskDefinition persisted = definitionRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "实时 Canvas 定义不存在"));
            TaskStreamingDeployment active = deploymentRepository
                    .findByTaskIdAndDefinitionVersion(taskId, persisted.getVersion())
                    .filter(item -> item.getActualState().active())
                    .orElse(null);
            return new StartSource(
                    task.getComputeEngineId(),
                    persisted.getVersion(),
                    definitionService.deserialize(persisted.getDefinitionJson()),
                    requireConfiguration(taskId).getTriggerIntervalSeconds(),
                    active
            );
        }));
    }

    private TaskStreamingDeployment requireOrCreateDeployment(
            UUID taskId,
            StartSource source,
            ExecutionRoute route
    ) {
        DataTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (task.getType() != TaskType.SPARK_STREAMING_CANVAS || task.getStatus() != TaskStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "实时任务状态已变化");
        }
        TaskStreamingDeployment deployment = deploymentRepository
                .findByTaskIdAndDefinitionVersionForUpdate(taskId, source.definitionVersion())
                .orElse(null);
        if (deployment == null) {
            deployment = deploymentRepository.saveAndFlush(TaskStreamingDeployment.create(
                    taskId,
                    source.definitionVersion(),
                    route.engineId(),
                    "streaming/%s/definitions/%d".formatted(taskId, source.definitionVersion())
            ));
            List<TaskStreamingQuery> queries = outputQueries(source.definition(), deployment);
            queryRepository.saveAllAndFlush(queries);
        }
        return deployment;
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
        return definition.nodes().stream().filter(node ->
                        node instanceof CanvasDefinition.KafkaOutputNodeDefinition
                                || node instanceof CanvasDefinition.JdbcOutputNodeDefinition)
                .map(node -> TaskStreamingQuery.create(
                        deployment.getId(),
                        UUID.fromString(node.id()),
                        node.name(),
                        node instanceof CanvasDefinition.KafkaOutputNodeDefinition
                                ? StreamingSinkType.KAFKA : StreamingSinkType.JDBC,
                        deployment.getCheckpointKeyPrefix() + "/outputs/" + node.id()
                )).toList();
    }

    private TaskStreamingStatusResponse status(UUID taskId, TaskStreamingDeployment deployment) {
        if (deployment == null) return new TaskStreamingStatusResponse(taskId, null);
        TaskRun run = deployment.getCurrentRunId() == null
                ? null : runRepository.findById(deployment.getCurrentRunId()).orElse(null);
        List<TaskStreamingQueryResponse> queries = queryRepository
                .findAllByDeploymentIdOrderByOutputNodeNameAsc(deployment.getId())
                .stream().map(TaskStreamingQueryResponse::from).toList();
        return new TaskStreamingStatusResponse(
                taskId, TaskStreamingDeploymentResponse.from(deployment, run, queries));
    }

    private DataTask requireStreamingTask(UUID taskId) {
        DataTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (task.getType() != TaskType.SPARK_STREAMING_CANVAS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不是 Spark 实时编排任务");
        }
        return task;
    }

    private DataTask requireStreamingTaskForUpdate(UUID taskId) {
        DataTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (task.getType() != TaskType.SPARK_STREAMING_CANVAS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不是 Spark 实时编排任务");
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
            int triggerIntervalSeconds,
            TaskStreamingDeployment activeDeployment
    ) {
    }
}
