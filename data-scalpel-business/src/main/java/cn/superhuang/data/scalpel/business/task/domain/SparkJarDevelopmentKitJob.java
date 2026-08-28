package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_spark_jar_development_kit_job", indexes = {
        @Index(name = "idx_spark_jar_kit_claim", columnList = "status,available_at,created_at"),
        @Index(name = "idx_spark_jar_kit_task", columnList = "task_id,created_at"),
        @Index(name = "idx_spark_jar_kit_lease", columnList = "status,lease_expires_at"),
        @Index(name = "idx_spark_jar_kit_expiry", columnList = "status,artifact_expires_at")
})
public class SparkJarDevelopmentKitJob extends BaseEntity {
    @Column(name = "task_id", nullable = false, updatable = false) private UUID taskId;
    @Column(name = "task_name_snapshot", nullable = false, length = 100, updatable = false) private String taskNameSnapshot;
    @Column(name = "definition_version", nullable = false, updatable = false) private int definitionVersion;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "request_json", nullable = false, updatable = false) private String requestJson;
    @Column(name = "requested_by", nullable = false, length = 100, updatable = false) private String requestedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private SparkJarDevelopmentKitStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private SparkJarDevelopmentKitStage stage;
    @Column(name = "progress_percent", nullable = false) private int progressPercent;
    @Column(name = "current_model", length = 100) private String currentModel;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "available_at", nullable = false) private Instant availableAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "lease_owner", length = 200) private String leaseOwner;
    @Column(name = "lease_expires_at") private Instant leaseExpiresAt;
    @Column(name = "last_heartbeat_at") private Instant lastHeartbeatAt;
    @Column(name = "artifact_object_key", length = 500) private String artifactObjectKey;
    @Column(name = "artifact_file_name", length = 255) private String artifactFileName;
    @Column(name = "artifact_size_bytes") private Long artifactSizeBytes;
    @Column(name = "artifact_sha256", length = 64) private String artifactSha256;
    @Column(name = "artifact_expires_at") private Instant artifactExpiresAt;
    @Column(name = "error_code", length = 64) private String errorCode;
    @Column(name = "error_message", length = 2000) private String errorMessage;

    protected SparkJarDevelopmentKitJob() {}

    public static SparkJarDevelopmentKitJob queue(UUID taskId, String taskName, int definitionVersion,
                                                   String requestJson, String requestedBy, Instant now) {
        SparkJarDevelopmentKitJob job = new SparkJarDevelopmentKitJob();
        job.taskId = taskId; job.taskNameSnapshot = taskName; job.definitionVersion = definitionVersion;
        job.requestJson = requestJson; job.requestedBy = requestedBy; job.status = SparkJarDevelopmentKitStatus.QUEUED;
        job.stage = SparkJarDevelopmentKitStage.QUEUED; job.availableAt = now;
        return job;
    }

    public void claim(String owner, Instant now, Instant leaseUntil) {
        if (status != SparkJarDevelopmentKitStatus.QUEUED || attemptCount >= 3) throw new IllegalStateException("生成任务不能被领取");
        status = SparkJarDevelopmentKitStatus.RUNNING; stage = SparkJarDevelopmentKitStage.VALIDATING;
        attemptCount++; startedAt = startedAt == null ? now : startedAt; leaseOwner = owner;
        leaseExpiresAt = leaseUntil; lastHeartbeatAt = now; errorCode = null; errorMessage = null;
    }

    public void progress(String owner, SparkJarDevelopmentKitStage value, int percent, String model, Instant now, Instant leaseUntil) {
        requireLease(owner); stage = value; progressPercent = Math.max(progressPercent, Math.min(99, percent));
        currentModel = model; lastHeartbeatAt = now; leaseExpiresAt = leaseUntil;
    }

    public void heartbeat(String owner, Instant now, Instant leaseUntil) {
        requireLease(owner); lastHeartbeatAt = now; leaseExpiresAt = leaseUntil;
    }

    public void succeed(String owner, String key, String name, long size, String sha256, Instant now, Instant expiresAt) {
        requireLease(owner); status = SparkJarDevelopmentKitStatus.SUCCEEDED; stage = SparkJarDevelopmentKitStage.COMPLETED;
        progressPercent = 100; currentModel = null; artifactObjectKey = key; artifactFileName = name;
        artifactSizeBytes = size; artifactSha256 = sha256; completedAt = now; artifactExpiresAt = expiresAt; clearLease();
    }

    public void retryOrFail(String owner, String code, String message, boolean retryable, Instant now) {
        requireLease(owner); errorCode = code; errorMessage = truncate(message);
        if (retryable && attemptCount < 3) { status = SparkJarDevelopmentKitStatus.QUEUED; stage = SparkJarDevelopmentKitStage.QUEUED;
            availableAt = now.plusSeconds(5L * attemptCount); currentModel = null; clearLease(); }
        else { status = SparkJarDevelopmentKitStatus.FAILED; stage = SparkJarDevelopmentKitStage.FAILED;
            completedAt = now; currentModel = null; clearLease(); }
    }

    public void recover(Instant now) {
        if (status != SparkJarDevelopmentKitStatus.RUNNING || leaseExpiresAt == null || leaseExpiresAt.isAfter(now)) return;
        if (attemptCount < 3) { status = SparkJarDevelopmentKitStatus.QUEUED; stage = SparkJarDevelopmentKitStage.QUEUED; availableAt = now; }
        else { status = SparkJarDevelopmentKitStatus.FAILED; stage = SparkJarDevelopmentKitStage.FAILED; completedAt = now;
            errorCode = "WORKER_LEASE_EXPIRED"; errorMessage = "开发包生成服务意外中断，请重新生成"; }
        currentModel = null; clearLease();
    }

    public void expire(Instant now) {
        if (status != SparkJarDevelopmentKitStatus.SUCCEEDED) return;
        status = SparkJarDevelopmentKitStatus.EXPIRED; stage = SparkJarDevelopmentKitStage.EXPIRED;
        artifactObjectKey = null; artifactExpiresAt = now;
    }

    /** Marks a no-longer-current artifact for asynchronous deletion. */
    public void scheduleArtifactCleanup(Instant now) {
        if (status == SparkJarDevelopmentKitStatus.SUCCEEDED && artifactObjectKey != null) {
            artifactExpiresAt = now;
        }
    }

    /** Keeps a legacy fallback package as the task's current package. */
    public void retainArtifact() {
        if (status == SparkJarDevelopmentKitStatus.SUCCEEDED && artifactObjectKey != null) {
            artifactExpiresAt = null;
        }
    }

    private void requireLease(String owner) {
        if (status != SparkJarDevelopmentKitStatus.RUNNING || !java.util.Objects.equals(leaseOwner, owner))
            throw new IllegalStateException("生成任务租约无效");
    }
    private void clearLease() { leaseOwner = null; leaseExpiresAt = null; lastHeartbeatAt = null; }
    private static String truncate(String value) { if (value == null || value.isBlank()) return "生成失败"; return value.length() <= 2000 ? value : value.substring(0, 2000); }

    public UUID getTaskId(){return taskId;} public String getTaskNameSnapshot(){return taskNameSnapshot;}
    public int getDefinitionVersion(){return definitionVersion;} public String getRequestJson(){return requestJson;}
    public String getRequestedBy(){return requestedBy;} public SparkJarDevelopmentKitStatus getStatus(){return status;}
    public SparkJarDevelopmentKitStage getStage(){return stage;} public int getProgressPercent(){return progressPercent;}
    public String getCurrentModel(){return currentModel;} public int getAttemptCount(){return attemptCount;}
    public Instant getStartedAt(){return startedAt;} public Instant getCompletedAt(){return completedAt;}
    public Instant getLeaseExpiresAt(){return leaseExpiresAt;} public String getArtifactObjectKey(){return artifactObjectKey;}
    public String getArtifactFileName(){return artifactFileName;} public Long getArtifactSizeBytes(){return artifactSizeBytes;}
    public String getArtifactSha256(){return artifactSha256;} public Instant getArtifactExpiresAt(){return artifactExpiresAt;}
    public String getErrorCode(){return errorCode;} public String getErrorMessage(){return errorMessage;}
}
