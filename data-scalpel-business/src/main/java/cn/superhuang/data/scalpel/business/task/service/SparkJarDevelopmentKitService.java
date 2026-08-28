package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitJob;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitSampleMode;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitStage;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitStatus;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarTaskResourceBinding;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarDevelopmentKitJobRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarDevelopmentKitQueueRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskResourceBindingRepository;
import cn.superhuang.data.scalpel.business.task.web.request.CreateSparkJarDevelopmentKitRequest;
import cn.superhuang.data.scalpel.business.task.web.response.SparkJarDevelopmentKitResponse;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The job table is an internal asynchronous queue. The task definition owns the
 * saved local-development configuration and points to its one current artifact.
 */
@Service
public class SparkJarDevelopmentKitService {
    static final Duration LEASE = Duration.ofMinutes(2);
    private static final List<SparkJarDevelopmentKitStatus> ACTIVE = List.of(
            SparkJarDevelopmentKitStatus.QUEUED, SparkJarDevelopmentKitStatus.RUNNING);

    private final DataTaskRepository taskRepository;
    private final SparkJarTaskDefinitionRepository definitionRepository;
    private final SparkJarTaskResourceBindingRepository bindingRepository;
    private final SparkJarDevelopmentKitJobRepository jobRepository;
    private final SparkJarDevelopmentKitQueueRepository queueRepository;
    private final TaskRunArtifactStorage storage;
    private final ObjectMapper objectMapper;

    public SparkJarDevelopmentKitService(DataTaskRepository taskRepository,
            SparkJarTaskDefinitionRepository definitionRepository,
            SparkJarTaskResourceBindingRepository bindingRepository,
            SparkJarDevelopmentKitJobRepository jobRepository,
            SparkJarDevelopmentKitQueueRepository queueRepository,
            TaskRunArtifactStorage storage, ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.definitionRepository = definitionRepository;
        this.bindingRepository = bindingRepository;
        this.jobRepository = jobRepository;
        this.queueRepository = queueRepository;
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    /** Saves the requested configuration and queues one replacement package. */
    @Transactional
    public SparkJarDevelopmentKitResponse generate(UUID taskId, CreateSparkJarDevelopmentKitRequest request) {
        DataTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        requireBatchJar(task);
        SparkJarTaskDefinition definition = definitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "请先保存 Spark JAR 任务定义"));
        if (definition.getVersion() != request.definitionVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务定义版本已变化，请保存并重试");
        }
        if (jobRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务已有正在生成的开发包");
        }

        SparkJarDevelopmentKitGenerator.Request normalized = normalizeConfiguration(
                taskId, request.samples(), request.jdbcTables(), true);
        String json = writeConfiguration(normalized);
        definition.saveDevelopmentKitConfig(json);
        SparkJarDevelopmentKitJob job = jobRepository.saveAndFlush(SparkJarDevelopmentKitJob.queue(
                taskId, task.getName(), definition.getVersion(), json, currentUsername(), Instant.now()));
        definitionRepository.saveAndFlush(definition);
        return response(definition, normalized, job);
    }

    @Transactional(readOnly = true)
    public SparkJarDevelopmentKitResponse get(UUID taskId) {
        DataTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        requireBatchJar(task);
        SparkJarTaskDefinition definition = definitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "请先保存 Spark JAR 任务定义"));
        SparkJarDevelopmentKitGenerator.Request config = currentConfiguration(taskId, definition);
        return response(definition, config, jobRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId).orElse(null));
    }

    @Transactional
    public Optional<SparkJarDevelopmentKitJob> claimNext(String workerId) {
        Instant now = Instant.now();
        return queueRepository.lockNext(now).map(job -> {
            job.claim(workerId, now, now.plus(LEASE));
            return job;
        });
    }

    @Transactional
    public int recoverExpired() {
        Instant now = Instant.now();
        List<SparkJarDevelopmentKitJob> jobs = queueRepository.lockExpired(now);
        jobs.forEach(job -> job.recover(now));
        return jobs.size();
    }

    @Transactional
    public void progress(UUID jobId, String workerId, SparkJarDevelopmentKitStage stage, int percent, String model) {
        SparkJarDevelopmentKitJob job = jobRepository.findById(jobId).orElseThrow();
        job.progress(workerId, stage, percent, model, Instant.now(), Instant.now().plus(LEASE));
    }

    @Transactional
    public void heartbeat(UUID jobId, String workerId) {
        Instant now = Instant.now();
        jobRepository.findById(jobId).orElseThrow().heartbeat(workerId, now, now.plus(LEASE));
    }

    /** Atomically replaces the task's current package only after the new object is available. */
    @Transactional
    public void succeed(UUID jobId, String workerId, String key, String name, long size, String sha) {
        SparkJarDevelopmentKitJob job = jobRepository.findById(jobId).orElseThrow();
        // Definition saves and generation submissions take the same task lock, so the
        // pointer swap cannot overwrite a concurrent configuration update.
        taskRepository.findByIdForUpdate(job.getTaskId()).orElseThrow();
        SparkJarTaskDefinition definition = definitionRepository.findByTaskId(job.getTaskId()).orElseThrow();
        Instant now = Instant.now();
        Optional<SparkJarDevelopmentKitJob> previousArtifact = currentArtifact(definition, job.getTaskId());
        // Current packages deliberately do not get an expiry timestamp.
        job.succeed(workerId, key, name, size, sha, now, null);
        UUID previousId = definition.replaceCurrentDevelopmentKit(job.getId());
        UUID cleanupId = previousId != null ? previousId : previousArtifact.map(SparkJarDevelopmentKitJob::getId).orElse(null);
        if (cleanupId != null && !cleanupId.equals(job.getId())) {
            jobRepository.findByIdAndTaskId(cleanupId, job.getTaskId())
                    .ifPresent(previous -> previous.scheduleArtifactCleanup(now));
        }
        definitionRepository.save(definition);
    }

    @Transactional
    public void fail(UUID jobId, String workerId, String code, String message, boolean retryable) {
        jobRepository.findById(jobId).orElseThrow()
                .retryOrFail(workerId, code, message, retryable, Instant.now());
    }

    public ArtifactDownload artifact(UUID taskId) {
        SparkJarDevelopmentKitJob job = loadDownloadable(taskId);
        TaskRunArtifactStorage.ArtifactContent content = storage.openIfPresent(job.getArtifactObjectKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "开发包制品已不存在，请重新生成"));
        return new ArtifactDownload(job.getArtifactFileName(), job.getArtifactSizeBytes(), content);
    }

    @Transactional(readOnly = true)
    protected SparkJarDevelopmentKitJob loadDownloadable(UUID taskId) {
        DataTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        requireBatchJar(task);
        SparkJarTaskDefinition definition = definitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "请先保存 Spark JAR 任务定义"));
        SparkJarDevelopmentKitJob job = currentArtifact(definition, taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "尚未生成可下载的开发包"));
        if (job.getStatus() != SparkJarDevelopmentKitStatus.SUCCEEDED || job.getArtifactObjectKey() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "开发包尚不可下载");
        }
        return job;
    }

    /** Cleans replaced legacy artifacts; the current artifact always has a null expiry. */
    public void expireArtifacts() {
        for (SparkJarDevelopmentKitJob job : expiredCandidates()) {
            String key = job.getArtifactObjectKey();
            if (key == null) continue;
            if (isCurrentArtifact(job)) {
                retainArtifact(job.getId());
                continue;
            }
            try {
                storage.delete(key);
                markExpired(job.getId());
            } catch (RuntimeException ignored) {
                // Keep the eligible row so the existing periodic cleanup can retry.
            }
        }
    }

    @Transactional(readOnly = true)
    protected List<SparkJarDevelopmentKitJob> expiredCandidates() {
        return jobRepository.findTop100ByStatusAndArtifactExpiresAtLessThanEqualOrderByArtifactExpiresAtAsc(
                SparkJarDevelopmentKitStatus.SUCCEEDED, Instant.now());
    }

    @Transactional
    protected void markExpired(UUID id) {
        jobRepository.findById(id).ifPresent(job -> {
            job.expire(Instant.now());
            jobRepository.save(job);
        });
    }

    @Transactional
    protected void retainArtifact(UUID id) {
        jobRepository.findById(id).ifPresent(job -> {
            job.retainArtifact();
            jobRepository.save(job);
        });
    }

    /** Called from task deletion in the same transaction; storage deletion is delayed until commit. */
    public void deleteForTask(UUID taskId) {
        if (jobRepository.existsByTaskIdAndStatusIn(taskId, ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "开发包正在生成，暂不能删除任务");
        }
        List<String> keys = jobRepository.findAllByTaskId(taskId).stream()
                .map(SparkJarDevelopmentKitJob::getArtifactObjectKey)
                .filter(value -> value != null && !value.isBlank())
                .toList();
        jobRepository.deleteAllByTaskId(taskId);
        if (keys.isEmpty()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                keys.forEach(key -> {
                    try {
                        storage.delete(key);
                    } catch (RuntimeException ignored) {
                        // Object-store cleanup is best effort; the object is no longer referenced.
                    }
                });
            }
        });
    }

    private SparkJarDevelopmentKitResponse response(SparkJarTaskDefinition definition,
            SparkJarDevelopmentKitGenerator.Request config, SparkJarDevelopmentKitJob latest) {
        Optional<SparkJarDevelopmentKitJob> artifactJob = currentArtifact(definition, definition.getTaskId());
        SparkJarDevelopmentKitResponse.Artifact artifact = artifactJob
                .filter(job -> job.getStatus() == SparkJarDevelopmentKitStatus.SUCCEEDED
                        && job.getArtifactObjectKey() != null)
                .map(job -> new SparkJarDevelopmentKitResponse.Artifact(
                        job.getArtifactSizeBytes(), job.getArtifactSha256(), job.getCompletedAt(),
                        definition.getDevelopmentKitConfigJson() != null
                                && definition.getDevelopmentKitConfigJson().equals(job.getRequestJson())))
                .orElse(null);
        return new SparkJarDevelopmentKitResponse(definition.getTaskId(), definition.getVersion(),
                toResponseConfig(config), latest == null ? null : generation(latest), artifact);
    }

    private Optional<SparkJarDevelopmentKitJob> currentArtifact(SparkJarTaskDefinition definition, UUID taskId) {
        UUID currentId = definition.getCurrentDevelopmentKitJobId();
        if (currentId != null) return jobRepository.findByIdAndTaskId(currentId, taskId);
        // Existing installations did not have a pointer. Keep their latest completed package visible
        // until the next successful replacement moves the task to the single-package semantics.
        return jobRepository.findFirstByTaskIdAndStatusOrderByCreatedAtDesc(taskId,
                SparkJarDevelopmentKitStatus.SUCCEEDED);
    }

    private boolean isCurrentArtifact(SparkJarDevelopmentKitJob job) {
        return definitionRepository.findByTaskId(job.getTaskId())
                .flatMap(definition -> currentArtifact(definition, job.getTaskId()))
                .map(current -> current.getId().equals(job.getId()))
                .orElse(false);
    }

    private SparkJarDevelopmentKitGenerator.Request currentConfiguration(UUID taskId, SparkJarTaskDefinition definition) {
        SparkJarDevelopmentKitGenerator.Request saved = readConfiguration(definition.getDevelopmentKitConfigJson());
        if (saved == null) {
            saved = jobRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId)
                    .map(job -> readConfiguration(job.getRequestJson())).orElse(null);
        }
        return normalizeConfiguration(taskId,
                saved == null ? List.of() : toInputRequests(saved.samples()),
                saved == null ? List.of() : toJdbcRequests(saved.jdbcTables()), false);
    }

    private SparkJarDevelopmentKitGenerator.Request normalizeConfiguration(UUID taskId,
            List<CreateSparkJarDevelopmentKitRequest.InputSample> requestedSamples,
            List<CreateSparkJarDevelopmentKitRequest.JdbcTableSample> requestedTables,
            boolean rejectInvalidBindings) {
        List<SparkJarTaskResourceBinding> bindings = bindingRepository.findAllByTaskIdOrderByCreatedAtAsc(taskId);
        Set<String> readableModels = new HashSet<>();
        Set<String> readableJdbc = new HashSet<>();
        for (SparkJarTaskResourceBinding binding : bindings) {
            if (!binding.getAccessMode().canRead()) continue;
            if (binding.getResourceType() == SparkJarResourceType.MODEL) readableModels.add(binding.getBindingName());
            if (binding.getResourceType() == SparkJarResourceType.JDBC_DATA_SOURCE) readableJdbc.add(binding.getBindingName());
        }

        Map<String, SparkJarDevelopmentKitGenerator.Sample> samples = new HashMap<>();
        for (CreateSparkJarDevelopmentKitRequest.InputSample sample : requestedSamples) {
            String bindingName = requiredBinding(sample.bindingName());
            if (!readableModels.contains(bindingName)) {
                if (rejectInvalidBindings) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "抽样配置不是输入模型绑定：" + bindingName);
                }
                continue;
            }
            if (samples.putIfAbsent(bindingName, new SparkJarDevelopmentKitGenerator.Sample(
                    bindingName, sample.mode(), sample.rowCount(), sample.percentage())) != null) {
                if (rejectInvalidBindings) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "输入模型抽样配置重复：" + bindingName);
                }
                continue;
            }
            validateSampleValue(sample.mode(), sample.rowCount(), sample.percentage());
        }
        List<SparkJarDevelopmentKitGenerator.Sample> normalizedSamples = new ArrayList<>();
        // Binding order is intentional: generated code and the saved configuration remain stable.
        for (SparkJarTaskResourceBinding binding : bindings) {
            if (!readableModels.contains(binding.getBindingName())) continue;
            normalizedSamples.add(samples.getOrDefault(binding.getBindingName(), new SparkJarDevelopmentKitGenerator.Sample(
                    binding.getBindingName(), SparkJarDevelopmentKitSampleMode.ROW_COUNT, 1_000, null)));
        }

        Set<String> tableKeys = new HashSet<>();
        List<SparkJarDevelopmentKitGenerator.JdbcTableSample> normalizedTables = new ArrayList<>();
        for (CreateSparkJarDevelopmentKitRequest.JdbcTableSample table : requestedTables) {
            String bindingName = requiredBinding(table.bindingName());
            if (!readableJdbc.contains(bindingName)) {
                if (rejectInvalidBindings) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JDBC 表不是可读取的数据源绑定：" + bindingName);
                }
                continue;
            }
            String catalog = normalizeOptional(table.catalog());
            String schema = normalizeOptional(table.schema());
            String tableName = requiredTable(table.table());
            String key = bindingName + "\u0000" + catalog + "\u0000" + schema + "\u0000" + tableName;
            if (!tableKeys.add(key)) {
                if (rejectInvalidBindings) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JDBC 表声明重复：" + tableName);
                }
                continue;
            }
            validateSampleValue(table.mode(), table.rowCount(), table.percentage());
            normalizedTables.add(new SparkJarDevelopmentKitGenerator.JdbcTableSample(
                    bindingName, catalog, schema, tableName, table.mode(), table.rowCount(), table.percentage()));
        }
        return new SparkJarDevelopmentKitGenerator.Request(normalizedSamples, normalizedTables);
    }

    private SparkJarDevelopmentKitResponse.Configuration toResponseConfig(SparkJarDevelopmentKitGenerator.Request request) {
        return new SparkJarDevelopmentKitResponse.Configuration(
                request.samples().stream().map(value -> new SparkJarDevelopmentKitResponse.InputSample(
                        value.bindingName(), value.mode(), value.rowCount(), value.percentage())).toList(),
                request.jdbcTables().stream().map(value -> new SparkJarDevelopmentKitResponse.JdbcTableSample(
                        value.bindingName(), value.catalog(), value.schema(), value.table(),
                        value.mode(), value.rowCount(), value.percentage())).toList());
    }

    private SparkJarDevelopmentKitResponse.Generation generation(SparkJarDevelopmentKitJob job) {
        return new SparkJarDevelopmentKitResponse.Generation(job.getId(), job.getDefinitionVersion(), job.getStatus(),
                job.getStage(), job.getProgressPercent(), job.getCurrentModel(), job.getAttemptCount(),
                job.getErrorCode(), job.getErrorMessage(), job.getCreatedAt(), job.getStartedAt(), job.getCompletedAt());
    }

    private String writeConfiguration(SparkJarDevelopmentKitGenerator.Request request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法保存开发包配置", exception);
        }
    }

    private SparkJarDevelopmentKitGenerator.Request readConfiguration(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, SparkJarDevelopmentKitGenerator.Request.class);
        } catch (RuntimeException exception) {
            // A bad legacy snapshot must not make the configuration page unavailable.
            return null;
        }
    }

    private static List<CreateSparkJarDevelopmentKitRequest.InputSample> toInputRequests(
            List<SparkJarDevelopmentKitGenerator.Sample> values) {
        return values.stream().map(value -> new CreateSparkJarDevelopmentKitRequest.InputSample(
                value.bindingName(), value.mode(), value.rowCount(), value.percentage())).toList();
    }

    private static List<CreateSparkJarDevelopmentKitRequest.JdbcTableSample> toJdbcRequests(
            List<SparkJarDevelopmentKitGenerator.JdbcTableSample> values) {
        return values.stream().map(value -> new CreateSparkJarDevelopmentKitRequest.JdbcTableSample(
                value.bindingName(), value.catalog(), value.schema(), value.table(),
                value.mode(), value.rowCount(), value.percentage())).toList();
    }

    private static void validateSampleValue(SparkJarDevelopmentKitSampleMode mode,
            Integer rowCount, BigDecimal percentage) {
        switch (mode) {
            case NONE, ALL -> {
                if (rowCount != null || percentage != null) throw invalidSample();
            }
            case ROW_COUNT -> {
                if (rowCount == null || percentage != null) throw invalidSample();
            }
            case PERCENTAGE -> {
                if (percentage == null || rowCount != null) throw invalidSample();
            }
        }
    }

    private static ResponseStatusException invalidSample() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "抽样方式与数值不匹配");
    }

    private static String requiredBinding(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资源绑定名不能为空");
        return normalized;
    }

    private static String requiredTable(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JDBC 表名不能为空");
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void requireBatchJar(DataTask task) {
        if (task.getType() != TaskType.SPARK_JAR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有批处理 Spark JAR 任务可以生成开发包");
        }
    }

    private static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication.getName() == null ? "unknown" : authentication.getName();
    }

    public record ArtifactDownload(String fileName, long size, TaskRunArtifactStorage.ArtifactContent content) {}
}
