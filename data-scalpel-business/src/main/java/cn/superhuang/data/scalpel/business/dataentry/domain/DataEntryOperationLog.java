package cn.superhuang.data.scalpel.business.dataentry.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "ds_data_entry_operation_log",
        indexes = {
                @Index(name = "idx_ds_data_entry_log_form_created", columnList = "form_id,created_at"),
                @Index(name = "idx_ds_data_entry_log_form_status_created", columnList = "form_id,status,created_at")
        }
)
public class DataEntryOperationLog extends BaseEntity {

    @Column(name = "form_id", nullable = false, updatable = false)
    private UUID formId;

    @Column(name = "model_id", updatable = false)
    private UUID modelId;

    @Column(name = "model_schema_version", updatable = false)
    private Integer modelSchemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 32, updatable = false)
    private DataEntryOperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DataEntryOperationStatus status;

    @Column(name = "operator_username", nullable = false, length = 100, updatable = false)
    private String operatorUsername;

    @Column(name = "requested_count", nullable = false, updatable = false)
    private int requestedCount;

    @Column(name = "affected_count")
    private Integer affectedCount;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "payload_snapshot", nullable = false)
    private String payloadSnapshot;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    protected DataEntryOperationLog() {
    }

    private DataEntryOperationLog(
            UUID formId,
            UUID modelId,
            Integer modelSchemaVersion,
            DataEntryOperationType operationType,
            String operatorUsername,
            int requestedCount,
            String payloadSnapshot
    ) {
        this.formId = Objects.requireNonNull(formId, "表单 ID 不能为空");
        this.modelId = modelId;
        this.modelSchemaVersion = modelSchemaVersion;
        this.operationType = Objects.requireNonNull(operationType, "操作类型不能为空");
        this.status = DataEntryOperationStatus.PROCESSING;
        this.operatorUsername = normalizeOperator(operatorUsername);
        this.requestedCount = requestedCount;
        this.payloadSnapshot = Objects.requireNonNull(payloadSnapshot, "请求快照不能为空");
    }

    public static DataEntryOperationLog processing(
            UUID formId,
            UUID modelId,
            Integer modelSchemaVersion,
            DataEntryOperationType operationType,
            String operatorUsername,
            int requestedCount,
            String payloadSnapshot
    ) {
        return new DataEntryOperationLog(
                formId, modelId, modelSchemaVersion, operationType, operatorUsername, requestedCount, payloadSnapshot
        );
    }

    public void succeed(int affectedCount, String normalizedPayload) {
        status = DataEntryOperationStatus.SUCCEEDED;
        this.affectedCount = affectedCount;
        this.payloadSnapshot = Objects.requireNonNull(normalizedPayload, "规范化请求快照不能为空");
        completedAt = Instant.now();
        errorCode = null;
        errorMessage = null;
    }

    public void fail(String errorCode, String safeMessage) {
        status = DataEntryOperationStatus.FAILED;
        affectedCount = null;
        completedAt = Instant.now();
        this.errorCode = truncate(errorCode, 100);
        this.errorMessage = truncate(safeMessage, 2000);
    }

    public void partiallySucceed(
            int affectedCount,
            String normalizedPayload,
            String errorCode,
            String safeMessage
    ) {
        status = DataEntryOperationStatus.PARTIALLY_SUCCEEDED;
        this.affectedCount = affectedCount;
        this.payloadSnapshot = Objects.requireNonNull(normalizedPayload, "规范化请求快照不能为空");
        completedAt = Instant.now();
        this.errorCode = truncate(errorCode, 100);
        this.errorMessage = truncate(safeMessage, 2000);
    }

    public UUID getFormId() { return formId; }
    public UUID getModelId() { return modelId; }
    public Integer getModelSchemaVersion() { return modelSchemaVersion; }
    public DataEntryOperationType getOperationType() { return operationType; }
    public DataEntryOperationStatus getStatus() { return status; }
    public String getOperatorUsername() { return operatorUsername; }
    public int getRequestedCount() { return requestedCount; }
    public Integer getAffectedCount() { return affectedCount; }
    public String getPayloadSnapshot() { return payloadSnapshot; }
    public Instant getCompletedAt() { return completedAt; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }

    private static String normalizeOperator(String value) {
        String normalized = value == null || value.isBlank() ? "unknown" : value.trim();
        return truncate(normalized, 100);
    }

    private static String truncate(String value, int maximumLength) {
        if (value == null) return null;
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }
}
