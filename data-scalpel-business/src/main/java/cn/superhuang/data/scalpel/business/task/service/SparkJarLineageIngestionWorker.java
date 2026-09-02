package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.SparkJarLineageIngestion;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.task.TaskLineageEvidence;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class SparkJarLineageIngestionWorker {
    private static final Logger log = LoggerFactory.getLogger(SparkJarLineageIngestionWorker.class);
    private static final int MAXIMUM_RESULT_BYTES = 5 * 1024 * 1024;

    private final SparkJarLineageIngestionService service;
    private final TaskRunArtifactStorage storage;
    private final ObjectMapper objectMapper;

    public SparkJarLineageIngestionWorker(
            SparkJarLineageIngestionService service,
            TaskRunArtifactStorage storage,
            ObjectMapper objectMapper
    ) {
        this.service = service;
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    public boolean runOne(String workerId) {
        Optional<SparkJarLineageIngestion> claimed = service.claimNext(workerId);
        if (claimed.isEmpty()) return false;
        SparkJarLineageIngestion job = claimed.get();
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon(true).name("spark-jar-lineage-heartbeat").factory());
        heartbeat.scheduleWithFixedDelay(() -> {
            try { service.heartbeat(job.getId(), workerId); }
            catch (RuntimeException exception) {
                log.debug("Spark JAR 运行血缘心跳失败，ingestionId={}", job.getId(), exception);
            }
        }, 20, 20, TimeUnit.SECONDS);
        try {
            byte[] content = storage.readIfPresent(job.getResultObjectKey(), MAXIMUM_RESULT_BYTES)
                    .orElseThrow(() -> new RetryableIngestionException(
                            "LINEAGE_RESULT_NOT_VISIBLE", "运行结果制品暂时不可见"));
            if (!MessageDigest.isEqual(
                    sha256(content).getBytes(StandardCharsets.US_ASCII),
                    job.getResultSha256().getBytes(StandardCharsets.US_ASCII))) {
                throw new StableIngestionException(
                        "LINEAGE_RESULT_SHA_MISMATCH", "运行结果制品摘要不一致");
            }
            ResultEnvelope result = objectMapper.readValue(content, ResultEnvelope.class);
            if (result.schemaVersion() == null
                    || result.schemaVersion() != 8 && result.schemaVersion() != 9
                    || !job.getExecutionRunId().equals(result.runId())
                    || result.taskType() != ExecutionTaskType.SPARK_JAR
                    || job.isRunSucceeded() != "SUCCESS".equals(result.state())) {
                throw new StableIngestionException(
                        "LINEAGE_RESULT_IDENTITY_INVALID", "运行结果制品身份或状态不一致");
            }
            TaskLineageEvidence evidence = result.lineage() == null
                    ? TaskLineageEvidence.unavailable(
                            "RESULT_LINEAGE_NOT_AVAILABLE", "运行在创建 Spark JAR 上下文前失败")
                    : result.lineage();
            service.complete(job.getId(), workerId, evidence);
            return true;
        } catch (StableIngestionException exception) {
            service.fail(job.getId(), workerId, exception.code, exception.getMessage(), false);
            return true;
        } catch (RetryableIngestionException exception) {
            service.fail(job.getId(), workerId, exception.code, exception.getMessage(), true);
            return true;
        } catch (IllegalArgumentException exception) {
            log.info("Spark JAR 运行血缘引用或结构已变化，ingestionId={}", job.getId());
            service.fail(job.getId(), workerId,
                    "LINEAGE_RESULT_INVALID_OR_RESOURCE_CHANGED",
                    "运行血缘结果无效，或其引用的模型、数据源结构已经变化", false);
            return true;
        } catch (RuntimeException exception) {
            log.warn("Spark JAR 运行血缘摄取失败，ingestionId={}", job.getId(), exception);
            service.fail(job.getId(), workerId,
                    "LINEAGE_INGESTION_TEMPORARY_FAILURE", "运行血缘摄取遇到临时故障", true);
            return true;
        } finally {
            heartbeat.shutdownNow();
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResultEnvelope(
            Integer schemaVersion,
            UUID runId,
            String state,
            ExecutionTaskType taskType,
            TaskLineageEvidence lineage
    ) {
    }

    private static final class StableIngestionException extends RuntimeException {
        private final String code;
        private StableIngestionException(String code, String message) { super(message); this.code = code; }
    }

    private static final class RetryableIngestionException extends RuntimeException {
        private final String code;
        private RetryableIngestionException(String code, String message) { super(message); this.code = code; }
    }
}
