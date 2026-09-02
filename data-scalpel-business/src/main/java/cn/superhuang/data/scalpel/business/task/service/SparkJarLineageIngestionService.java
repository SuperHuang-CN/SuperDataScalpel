package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;
import cn.superhuang.data.scalpel.business.lineage.service.TaskLineageSnapshotService;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarLineageAggregate;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarLineageIngestion;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunExecutionMode;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarLineageAggregateRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarLineageIngestionRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarLineageQueueRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskDefinitionRepository;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.task.TaskLineageEvidence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SparkJarLineageIngestionService {
    static final Duration LEASE = Duration.ofMinutes(2);

    private final SparkJarLineageIngestionRepository ingestionRepository;
    private final SparkJarLineageAggregateRepository aggregateRepository;
    private final SparkJarLineageQueueRepository queueRepository;
    private final DataTaskRepository taskRepository;
    private final SparkJarTaskDefinitionRepository definitionRepository;
    private final TaskLineageSnapshotService snapshotService;
    private final SparkJarLineageDraftFactory draftFactory;
    private final ObjectMapper objectMapper;

    public SparkJarLineageIngestionService(
            SparkJarLineageIngestionRepository ingestionRepository,
            SparkJarLineageAggregateRepository aggregateRepository,
            SparkJarLineageQueueRepository queueRepository,
            DataTaskRepository taskRepository,
            SparkJarTaskDefinitionRepository definitionRepository,
            TaskLineageSnapshotService snapshotService,
            SparkJarLineageDraftFactory draftFactory,
            ObjectMapper objectMapper
    ) {
        this.ingestionRepository = ingestionRepository;
        this.aggregateRepository = aggregateRepository;
        this.queueRepository = queueRepository;
        this.taskRepository = taskRepository;
        this.definitionRepository = definitionRepository;
        this.snapshotService = snapshotService;
        this.draftFactory = draftFactory;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueue(TaskRun run, DispatcherExecutionEvent event) {
        if (run.getExecutionMode() == TaskRunExecutionMode.TRIAL
                || event.resultSha256() == null || ingestionRepository.existsByRunId(run.getId())) return;
        String jarSha256 = run.getUserJarSha256();
        String resultObjectKey = run.getResultObjectKey();
        if (jarSha256 == null || resultObjectKey == null) return;
        boolean succeeded = event.messageType() == ExecutionMessageType.EXECUTION_SUCCEEDED;
        ingestionRepository.save(SparkJarLineageIngestion.queue(
                run.getId(), run.getExecutionRunId(), run.getTaskId(), run.getDefinitionVersion(), jarSha256,
                resultObjectKey, event.resultSha256(), succeeded, Instant.now()));
    }

    @Transactional
    public Optional<SparkJarLineageIngestion> claimNext(String workerId) {
        Instant now = Instant.now();
        return queueRepository.lockNext(now).map(job -> {
            job.claim(workerId, now, now.plus(LEASE));
            return job;
        });
    }

    @Transactional
    public int recoverExpired() {
        Instant now = Instant.now();
        List<SparkJarLineageIngestion> jobs = queueRepository.lockExpired(now);
        jobs.forEach(job -> job.recover(now));
        return jobs.size();
    }

    @Transactional
    public void heartbeat(UUID id, String workerId) {
        SparkJarLineageIngestion job = ingestionRepository.findById(id).orElseThrow();
        job.heartbeat(workerId, Instant.now().plus(LEASE));
    }

    @Transactional
    public void complete(UUID id, String workerId, TaskLineageEvidence evidence) {
        SparkJarLineageIngestion job = ingestionRepository.findById(id).orElseThrow();
        DataTask task = taskRepository.findByIdForUpdate(job.getTaskId()).orElseThrow();
        SparkJarTaskDefinition definition = definitionRepository.findByTaskId(job.getTaskId()).orElse(null);
        LineageCoverage coverage = coverage(evidence.coverage());
        String warningsJson = warningJson(evidence.warnings());
        int warningCount = evidence.warnings().size();

        boolean current = definition != null
                && definition.getVersion() == job.getDefinitionVersion()
                && job.getJarSha256().equals(definition.getJarSha256());
        if (!current) {
            job.stale(workerId, coverage, evidence.flows().size(), warningCount, warningsJson, Instant.now());
            return;
        }
        if (!job.isRunSucceeded() || evidence.flows().isEmpty()) {
            job.succeed(workerId, coverage, evidence.flows().size(), warningCount,
                    warningsJson, false, Instant.now());
            return;
        }

        SparkJarLineageAggregate aggregate = aggregateRepository.findForUpdate(
                job.getTaskId(), job.getDefinitionVersion(), job.getJarSha256()).orElse(null);
        TaskLineageEvidence merged = aggregate == null
                ? evidence
                : merge(readEvidence(aggregate.getEvidenceJson()), evidence);
        String json = objectMapper.writeValueAsString(merged);
        String digest = sha256(json.getBytes(StandardCharsets.UTF_8));
        if (aggregate == null) {
            aggregate = SparkJarLineageAggregate.create(
                    job.getTaskId(), job.getDefinitionVersion(), job.getJarSha256(), json, digest);
        } else if (!aggregate.getContentSha256().equals(digest)) {
            aggregate.replace(json, digest);
        }
        aggregateRepository.saveAndFlush(aggregate);
        snapshotService.publish(draftFactory.create(task.getId(), job.getDefinitionVersion(), merged));
        job.succeed(workerId, coverage(merged.coverage()), merged.flows().size(),
                merged.warnings().size(), warningJson(merged.warnings()), true, Instant.now());
    }

    @Transactional
    public void fail(UUID id, String workerId, String code, String detail, boolean retryable) {
        ingestionRepository.findById(id).orElseThrow()
                .retryOrFail(workerId, code, detail, retryable, Instant.now());
    }

    @Transactional(readOnly = true)
    public Optional<SparkJarLineageIngestion> find(UUID runId) {
        return ingestionRepository.findByRunId(runId);
    }

    private TaskLineageEvidence readEvidence(String json) {
        return objectMapper.readValue(json, TaskLineageEvidence.class);
    }

    private String warningJson(List<TaskLineageEvidence.Warning> warnings) {
        return objectMapper.writeValueAsString(warnings.stream().map(warning ->
                new SafeWarning(warning.code(), warning.message(), warning.flowKey())).toList());
    }

    static TaskLineageEvidence merge(TaskLineageEvidence left, TaskLineageEvidence right) {
        Map<String, TaskLineageEvidence.Flow> flows = new LinkedHashMap<>();
        left.flows().forEach(flow -> flows.put(flow.flowKey(), flow));
        right.flows().forEach(flow -> flows.merge(flow.flowKey(), flow,
                SparkJarLineageIngestionService::mergeFlow));
        List<TaskLineageEvidence.Flow> mergedFlows = flows.values().stream()
                .sorted(Comparator.comparing(TaskLineageEvidence.Flow::flowKey)).toList();
        TaskLineageEvidence.Coverage coverage = mergedFlows.stream()
                .map(TaskLineageEvidence.Flow::coverage)
                .min(Comparator.comparingInt(SparkJarLineageIngestionService::coverageRank))
                .orElse(null);
        TaskLineageEvidence.AnalysisStatus status = mergedFlows.isEmpty()
                ? TaskLineageEvidence.AnalysisStatus.UNAVAILABLE
                : coverage == TaskLineageEvidence.Coverage.FIELD_COMPLETE
                ? TaskLineageEvidence.AnalysisStatus.COMPLETE : TaskLineageEvidence.AnalysisStatus.PARTIAL;
        List<TaskLineageEvidence.Warning> warnings = union(left.warnings(), right.warnings());
        return new TaskLineageEvidence(status, coverage, mergedFlows, warnings);
    }

    private static TaskLineageEvidence.Flow mergeFlow(
            TaskLineageEvidence.Flow left, TaskLineageEvidence.Flow right
    ) {
        return new TaskLineageEvidence.Flow(
                left.flowKey(), left.producerKey(), left.producerType(),
                coverageRank(left.coverage()) <= coverageRank(right.coverage())
                        ? left.coverage() : right.coverage(),
                left.outputAsset(), union(left.inputAssets(), right.inputAssets()),
                union(left.fields(), right.fields()), union(left.fieldEdges(), right.fieldEdges()),
                union(left.fieldUsages(), right.fieldUsages()), union(left.warnings(), right.warnings()));
    }

    private static <T> List<T> union(List<T> left, List<T> right) {
        LinkedHashSet<T> values = new LinkedHashSet<>(left);
        values.addAll(right);
        return List.copyOf(values);
    }

    private static int coverageRank(TaskLineageEvidence.Coverage value) {
        return switch (value) {
            case MODEL_ONLY -> 0;
            case FIELD_PARTIAL -> 1;
            case FIELD_COMPLETE -> 2;
        };
    }

    private static LineageCoverage coverage(TaskLineageEvidence.Coverage value) {
        return value == null ? null : LineageCoverage.valueOf(value.name());
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record SafeWarning(String code, String message, String flowKey) {
    }
}
