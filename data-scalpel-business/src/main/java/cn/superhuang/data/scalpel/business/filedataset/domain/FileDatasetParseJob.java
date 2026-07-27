package cn.superhuang.data.scalpel.business.filedataset.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Durable execution history for file preparation and logical-table loading. */
@Entity
@Table(
        name = "ds_file_dataset_parse_job",
        indexes = {
                @Index(name = "idx_ds_file_parse_job_claim", columnList = "status,available_at,created_at"),
                @Index(name = "idx_ds_file_parse_job_table", columnList = "file_dataset_table_id,created_at"),
                @Index(name = "idx_ds_file_parse_job_source", columnList = "source_file_id,status"),
                @Index(name = "idx_ds_file_parse_job_lease", columnList = "status,lease_expires_at"),
                @Index(name = "idx_ds_file_parse_job_history", columnList = "status,completed_at")
        }
)
public class FileDatasetParseJob extends BaseEntity {

    public static final int MAX_ERROR_MESSAGE_LENGTH = 2_000;

    @Column(name = "file_dataset_id", nullable = false, updatable = false)
    private UUID fileDatasetId;

    @Column(name = "source_file_id", nullable = false, updatable = false)
    private UUID sourceFileId;

    @Column(name = "file_dataset_table_id", updatable = false)
    private UUID fileDatasetTableId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 32, updatable = false)
    private FileDatasetParseJobType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "load_mode", length = 32, updatable = false)
    private FileDatasetTableSourceLoadMode loadMode;

    @Column(name = "target_source_id", updatable = false)
    private UUID targetSourceId;

    @Column(name = "source_name", length = 255, updatable = false)
    private String sourceName;

    @Column(name = "source_key", length = 255, updatable = false)
    private String sourceKey;

    @Column(name = "dataset_name_snapshot", nullable = false, length = 100, updatable = false)
    private String datasetNameSnapshot;

    @Column(name = "table_name_snapshot", length = 255, updatable = false)
    private String tableNameSnapshot;

    @Column(name = "file_name_snapshot", nullable = false, length = 255, updatable = false)
    private String fileNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FileDatasetParseJobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false, updatable = false)
    private int maxAttempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "queued_at", nullable = false, updatable = false)
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "lease_owner", length = 200)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "error_message", length = MAX_ERROR_MESSAGE_LENGTH)
    private String errorMessage;

    protected FileDatasetParseJob() {
    }

    private FileDatasetParseJob(
            FileDatasetParseJobType type,
            UUID fileDatasetId,
            UUID sourceFileId,
            UUID fileDatasetTableId,
            FileDatasetTableSourceLoadMode loadMode,
            UUID targetSourceId,
            String sourceName,
            String sourceKey,
            String datasetNameSnapshot,
            String tableNameSnapshot,
            String fileNameSnapshot,
            int maxAttempts,
            Instant queuedAt
    ) {
        this.type = java.util.Objects.requireNonNull(type, "解析任务类型不能为空");
        this.fileDatasetId = java.util.Objects.requireNonNull(fileDatasetId, "解析任务的数据集不能为空");
        this.sourceFileId = java.util.Objects.requireNonNull(sourceFileId, "解析任务的来源文件不能为空");
        if (type == FileDatasetParseJobType.TABLE_SOURCE_VALIDATE
                && (fileDatasetTableId == null || loadMode == null)) {
            throw new IllegalArgumentException("表校验任务必须指定逻辑表和装载方式");
        }
        if (loadMode == FileDatasetTableSourceLoadMode.REPLACE_SOURCE && targetSourceId == null) {
            throw new IllegalArgumentException("来源替换任务必须指定目标来源");
        }
        if (loadMode != FileDatasetTableSourceLoadMode.REPLACE_SOURCE && targetSourceId != null) {
            throw new IllegalArgumentException("当前装载方式不能指定目标来源");
        }
        if (type == FileDatasetParseJobType.TABLE_SOURCE_VALIDATE
                && (!hasText(sourceName) || !hasText(sourceKey))) {
            throw new IllegalArgumentException("表校验任务必须保存来源名称和来源键");
        }
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException("解析任务最大尝试次数必须在 1 到 10 之间");
        }
        this.fileDatasetTableId = fileDatasetTableId;
        this.loadMode = loadMode;
        this.targetSourceId = targetSourceId;
        this.sourceName = optional(sourceName);
        this.sourceKey = optional(sourceKey);
        this.datasetNameSnapshot = required(datasetNameSnapshot, "数据集名称快照不能为空");
        this.tableNameSnapshot = optional(tableNameSnapshot);
        this.fileNameSnapshot = required(fileNameSnapshot, "文件名快照不能为空");
        this.maxAttempts = maxAttempts;
        this.queuedAt = requireInstant(queuedAt, "入队时间不能为空");
        this.availableAt = queuedAt;
        this.status = FileDatasetParseJobStatus.QUEUED;
    }

    public static FileDatasetParseJob queueTableValidation(
            UUID fileDatasetId,
            UUID sourceFileId,
            UUID fileDatasetTableId,
            FileDatasetTableSourceLoadMode loadMode,
            UUID targetSourceId,
            String sourceName,
            String sourceKey,
            String datasetNameSnapshot,
            String tableNameSnapshot,
            String fileNameSnapshot,
            int maxAttempts,
            Instant queuedAt
    ) {
        return new FileDatasetParseJob(
                FileDatasetParseJobType.TABLE_SOURCE_VALIDATE,
                fileDatasetId, sourceFileId, fileDatasetTableId, loadMode, targetSourceId,
                sourceName, sourceKey, datasetNameSnapshot, tableNameSnapshot, fileNameSnapshot,
                maxAttempts, queuedAt
        );
    }

    public static FileDatasetParseJob queueFilePreparation(
            UUID fileDatasetId,
            UUID sourceFileId,
            UUID fileDatasetTableId,
            FileDatasetTableSourceLoadMode loadMode,
            UUID targetSourceId,
            String sourceName,
            String sourceKey,
            String datasetNameSnapshot,
            String tableNameSnapshot,
            String fileNameSnapshot,
            int maxAttempts,
            Instant queuedAt
    ) {
        return new FileDatasetParseJob(
                FileDatasetParseJobType.FILE_PREPARATION,
                fileDatasetId, sourceFileId, fileDatasetTableId, loadMode, targetSourceId,
                sourceName, sourceKey, datasetNameSnapshot, tableNameSnapshot, fileNameSnapshot,
                maxAttempts, queuedAt
        );
    }

    public void claim(String workerId, Instant now, Instant leaseUntil) {
        requireStatus(FileDatasetParseJobStatus.QUEUED);
        String normalizedWorkerId = normalizeWorkerId(workerId);
        Instant claimTime = requireInstant(now, "领取时间不能为空");
        Instant normalizedLeaseUntil = requireInstant(leaseUntil, "租约过期时间不能为空");
        if (availableAt.isAfter(claimTime)) {
            throw new IllegalStateException("解析任务尚未到可执行时间");
        }
        if (!normalizedLeaseUntil.isAfter(claimTime)) {
            throw new IllegalArgumentException("租约过期时间必须晚于领取时间");
        }
        if (attemptCount >= maxAttempts) {
            throw new IllegalStateException("解析任务已耗尽最大尝试次数");
        }
        status = FileDatasetParseJobStatus.RUNNING;
        attemptCount++;
        startedAt = claimTime;
        completedAt = null;
        leaseOwner = normalizedWorkerId;
        leaseExpiresAt = normalizedLeaseUntil;
        lastHeartbeatAt = claimTime;
    }

    public void heartbeat(String workerId, Instant now, Instant leaseUntil) {
        Instant heartbeatTime = requireInstant(now, "心跳时间不能为空");
        requireActiveLease(workerId, heartbeatTime);
        Instant normalizedLeaseUntil = requireInstant(leaseUntil, "租约过期时间不能为空");
        if (!normalizedLeaseUntil.isAfter(heartbeatTime)) {
            throw new IllegalArgumentException("租约过期时间必须晚于心跳时间");
        }
        lastHeartbeatAt = heartbeatTime;
        leaseExpiresAt = normalizedLeaseUntil;
    }

    public void succeed(String workerId, Instant completedAt) {
        Instant normalizedCompletedAt = requireInstant(completedAt, "完成时间不能为空");
        requireActiveLease(workerId, normalizedCompletedAt);
        status = FileDatasetParseJobStatus.SUCCEEDED;
        this.completedAt = normalizedCompletedAt;
        errorMessage = null;
        clearLease();
    }

    public void retry(String workerId, String errorMessage, Instant failedAt, Instant nextAvailableAt) {
        Instant normalizedFailedAt = requireInstant(failedAt, "失败时间不能为空");
        requireActiveLease(workerId, normalizedFailedAt);
        if (!canRetry()) {
            throw new IllegalStateException("解析任务已耗尽最大尝试次数");
        }
        status = FileDatasetParseJobStatus.QUEUED;
        availableAt = requireInstant(nextAvailableAt, "下次执行时间不能为空");
        completedAt = null;
        this.errorMessage = normalizeError(errorMessage);
        clearLease();
    }

    public void fail(String workerId, String errorMessage, Instant completedAt) {
        Instant normalizedCompletedAt = requireInstant(completedAt, "失败时间不能为空");
        requireActiveLease(workerId, normalizedCompletedAt);
        finishFailure(errorMessage, normalizedCompletedAt);
    }

    public void cancel(String reason, Instant completedAt) {
        requireStatus(FileDatasetParseJobStatus.QUEUED);
        status = FileDatasetParseJobStatus.CANCELLED;
        this.completedAt = requireInstant(completedAt, "取消时间不能为空");
        errorMessage = normalizeError(reason);
        clearLease();
    }

    public void recoverExpiredLease(Instant now, Instant nextAvailableAt, String errorMessage) {
        requireStatus(FileDatasetParseJobStatus.RUNNING);
        Instant recoveryTime = requireInstant(now, "恢复时间不能为空");
        if (leaseExpiresAt == null || leaseExpiresAt.isAfter(recoveryTime)) {
            throw new IllegalStateException("解析任务租约尚未过期");
        }
        if (canRetry()) {
            status = FileDatasetParseJobStatus.QUEUED;
            availableAt = requireInstant(nextAvailableAt, "下次执行时间不能为空");
            completedAt = null;
            this.errorMessage = normalizeError(errorMessage);
            clearLease();
            return;
        }
        finishFailure(errorMessage, recoveryTime);
    }

    public void failExpiredLease(Instant now, String errorMessage) {
        requireStatus(FileDatasetParseJobStatus.RUNNING);
        Instant failureTime = requireInstant(now, "租约终止时间不能为空");
        if (leaseExpiresAt == null || leaseExpiresAt.isAfter(failureTime)) {
            throw new IllegalStateException("解析任务租约尚未过期");
        }
        finishFailure(errorMessage, failureTime);
    }

    public boolean canRetry() { return attemptCount < maxAttempts; }
    public UUID getFileDatasetId() { return fileDatasetId; }
    public UUID getSourceFileId() { return sourceFileId; }
    public UUID getFileDatasetTableId() { return fileDatasetTableId; }
    public FileDatasetParseJobType getType() { return type; }
    public FileDatasetTableSourceLoadMode getLoadMode() { return loadMode; }
    public UUID getTargetSourceId() { return targetSourceId; }
    public String getSourceName() { return sourceName; }
    public String getSourceKey() { return sourceKey; }
    public String getDatasetNameSnapshot() { return datasetNameSnapshot; }
    public String getTableNameSnapshot() { return tableNameSnapshot; }
    public String getFileNameSnapshot() { return fileNameSnapshot; }
    public FileDatasetParseJobStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getQueuedAt() { return queuedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getLeaseOwner() { return leaseOwner; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
    public String getErrorMessage() { return errorMessage; }

    private void finishFailure(String errorMessage, Instant completedAt) {
        status = FileDatasetParseJobStatus.FAILED;
        this.completedAt = requireInstant(completedAt, "失败时间不能为空");
        this.errorMessage = normalizeError(errorMessage);
        clearLease();
    }

    private void requireActiveLease(String workerId, Instant operationTime) {
        requireStatus(FileDatasetParseJobStatus.RUNNING);
        if (!normalizeWorkerId(workerId).equals(leaseOwner)) {
            throw new IllegalStateException("解析任务租约不属于当前 Worker");
        }
        if (leaseExpiresAt == null || !leaseExpiresAt.isAfter(operationTime)) {
            throw new IllegalStateException("解析任务租约已经过期");
        }
    }

    private void requireStatus(FileDatasetParseJobStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("解析任务状态不是 " + expected);
        }
    }

    private void clearLease() {
        leaseOwner = null;
        leaseExpiresAt = null;
    }

    private static String normalizeWorkerId(String workerId) {
        String normalized = required(workerId, "Worker 标识不能为空");
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("Worker 标识不能超过 200 个字符");
        }
        return normalized;
    }

    private static String normalizeError(String value) {
        String normalized = required(value, "解析任务错误摘要不能为空");
        return normalized.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? normalized : normalized.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private static String required(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String optional(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static Instant requireInstant(Instant value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
