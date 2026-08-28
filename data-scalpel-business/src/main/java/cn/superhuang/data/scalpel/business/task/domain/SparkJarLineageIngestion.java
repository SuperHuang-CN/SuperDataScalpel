package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;
import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "task_spark_jar_lineage_ingestion", uniqueConstraints =
        @UniqueConstraint(name = "uk_spark_jar_lineage_ingestion_run", columnNames = "run_id"), indexes = {
        @Index(name = "idx_spark_jar_lineage_ingestion_claim", columnList = "status,available_at,created_at"),
        @Index(name = "idx_spark_jar_lineage_ingestion_task", columnList = "task_id,created_at"),
        @Index(name = "idx_spark_jar_lineage_ingestion_lease", columnList = "status,lease_expires_at")
})
public class SparkJarLineageIngestion extends BaseEntity {
    @Column(name = "run_id", nullable = false, updatable = false) private UUID runId;
    @Column(name = "execution_run_id", nullable = false, updatable = false) private UUID executionRunId;
    @Column(name = "task_id", nullable = false, updatable = false) private UUID taskId;
    @Column(name = "definition_version", nullable = false, updatable = false) private int definitionVersion;
    @Column(name = "jar_sha256", nullable = false, length = 64, updatable = false) private String jarSha256;
    @Column(name = "result_object_key", nullable = false, length = 500, updatable = false) private String resultObjectKey;
    @Column(name = "result_sha256", nullable = false, length = 64, updatable = false) private String resultSha256;
    @Column(name = "run_succeeded", nullable = false, updatable = false) private boolean runSucceeded;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private SparkJarLineageIngestionStatus status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "available_at", nullable = false) private Instant availableAt;
    @Column(name = "lease_owner", length = 200) private String leaseOwner;
    @Column(name = "lease_expires_at") private Instant leaseExpiresAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Enumerated(EnumType.STRING) @Column(length = 32) private LineageCoverage coverage;
    @Column(name = "flow_count") private Integer flowCount;
    @Column(name = "warning_count") private Integer warningCount;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "warnings_json") private String warningsJson;
    @Column(name = "published_snapshot", nullable = false) private boolean publishedSnapshot;
    @Column(name = "error_code", length = 100) private String errorCode;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "error_detail") private String errorDetail;

    protected SparkJarLineageIngestion() {
    }

    public static SparkJarLineageIngestion queue(
            UUID runId, UUID executionRunId, UUID taskId, int definitionVersion, String jarSha256,
            String resultObjectKey, String resultSha256, boolean runSucceeded, Instant now
    ) {
        SparkJarLineageIngestion value = new SparkJarLineageIngestion();
        value.runId = Objects.requireNonNull(runId);
        value.executionRunId = Objects.requireNonNull(executionRunId);
        value.taskId = Objects.requireNonNull(taskId);
        value.definitionVersion = definitionVersion;
        value.jarSha256 = requiredSha(jarSha256);
        value.resultObjectKey = required(resultObjectKey);
        value.resultSha256 = requiredSha(resultSha256);
        value.runSucceeded = runSucceeded;
        value.status = SparkJarLineageIngestionStatus.QUEUED;
        value.availableAt = now;
        return value;
    }

    public void claim(String owner, Instant now, Instant leaseUntil) {
        if (status != SparkJarLineageIngestionStatus.QUEUED || attemptCount >= 3) {
            throw new IllegalStateException("运行血缘摄取任务不能被领取");
        }
        status = SparkJarLineageIngestionStatus.RUNNING;
        attemptCount++;
        startedAt = startedAt == null ? now : startedAt;
        leaseOwner = required(owner);
        leaseExpiresAt = leaseUntil;
        errorCode = null;
        errorDetail = null;
    }

    public void heartbeat(String owner, Instant leaseUntil) {
        requireLease(owner);
        leaseExpiresAt = leaseUntil;
    }

    public void succeed(String owner, LineageCoverage value, int flows, int warnings,
                        String warningJson, boolean published, Instant now) {
        requireLease(owner);
        status = SparkJarLineageIngestionStatus.SUCCEEDED;
        coverage = value;
        flowCount = flows;
        warningCount = warnings;
        warningsJson = warningJson;
        publishedSnapshot = published;
        completedAt = now;
        clearLease();
    }

    public void stale(String owner, LineageCoverage value, int flows, int warnings,
                      String warningJson, Instant now) {
        requireLease(owner);
        status = SparkJarLineageIngestionStatus.STALE;
        coverage = value;
        flowCount = flows;
        warningCount = warnings;
        warningsJson = warningJson;
        publishedSnapshot = false;
        completedAt = now;
        errorCode = "LINEAGE_DEFINITION_STALE";
        errorDetail = "任务定义或 JAR 已变化，本次运行血缘未进入正式快照";
        clearLease();
    }

    public void retryOrFail(String owner, String code, String detail, boolean retryable, Instant now) {
        requireLease(owner);
        errorCode = required(code);
        errorDetail = truncate(detail);
        if (retryable && attemptCount < 3) {
            status = SparkJarLineageIngestionStatus.QUEUED;
            availableAt = now.plusSeconds(5L * attemptCount);
        } else {
            status = SparkJarLineageIngestionStatus.FAILED;
            completedAt = now;
        }
        clearLease();
    }

    public void recover(Instant now) {
        if (status != SparkJarLineageIngestionStatus.RUNNING
                || leaseExpiresAt == null || leaseExpiresAt.isAfter(now)) return;
        if (attemptCount < 3) {
            status = SparkJarLineageIngestionStatus.QUEUED;
            availableAt = now;
        } else {
            status = SparkJarLineageIngestionStatus.FAILED;
            completedAt = now;
            errorCode = "LINEAGE_WORKER_LEASE_EXPIRED";
            errorDetail = "运行血缘摄取服务意外中断";
        }
        clearLease();
    }

    private void requireLease(String owner) {
        if (status != SparkJarLineageIngestionStatus.RUNNING || !Objects.equals(leaseOwner, owner)) {
            throw new IllegalStateException("运行血缘摄取租约无效");
        }
    }

    private void clearLease() { leaseOwner = null; leaseExpiresAt = null; }
    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("值不能为空");
        return value.trim();
    }
    private static String requiredSha(String value) {
        value = required(value);
        if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("SHA-256 无效");
        return value;
    }
    private static String truncate(String value) {
        if (value == null || value.isBlank()) return "运行血缘摄取失败";
        return value.length() <= 2_000 ? value : value.substring(0, 2_000);
    }

    public UUID getRunId() { return runId; }
    public UUID getExecutionRunId() { return executionRunId; }
    public UUID getTaskId() { return taskId; }
    public int getDefinitionVersion() { return definitionVersion; }
    public String getJarSha256() { return jarSha256; }
    public String getResultObjectKey() { return resultObjectKey; }
    public String getResultSha256() { return resultSha256; }
    public boolean isRunSucceeded() { return runSucceeded; }
    public SparkJarLineageIngestionStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public LineageCoverage getCoverage() { return coverage; }
    public Integer getFlowCount() { return flowCount; }
    public Integer getWarningCount() { return warningCount; }
    public String getWarningsJson() { return warningsJson; }
    public boolean isPublishedSnapshot() { return publishedSnapshot; }
    public String getErrorCode() { return errorCode; }
    public String getErrorDetail() { return errorDetail; }
}
