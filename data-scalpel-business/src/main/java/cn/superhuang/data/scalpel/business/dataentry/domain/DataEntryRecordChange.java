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
@Table(name = "ds_data_entry_record_change", indexes = {
        @Index(name = "idx_ds_entry_change_form_record_created", columnList = "form_id,record_key,created_at"),
        @Index(name = "idx_ds_entry_change_operation_sequence", columnList = "operation_log_id,sequence_no")
})
public class DataEntryRecordChange extends BaseEntity {

    @Column(name = "operation_log_id", nullable = false, updatable = false)
    private UUID operationLogId;
    @Column(name = "form_id", nullable = false, updatable = false)
    private UUID formId;
    @Column(name = "record_key", nullable = false, length = 64, updatable = false)
    private String recordKey;
    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 32, updatable = false)
    private DataEntryOperationType operationType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DataEntryRecordChangeStatus status;
    @Column(name = "operator_username", nullable = false, length = 100, updatable = false)
    private String operatorUsername;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "business_key_snapshot", nullable = false, updatable = false)
    private String businessKeySnapshot;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "field_snapshot", nullable = false, updatable = false)
    private String fieldSnapshot;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "submitted_snapshot", updatable = false)
    private String submittedSnapshot;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "before_snapshot", updatable = false)
    private String beforeSnapshot;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "after_snapshot")
    private String afterSnapshot;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "error_code", length = 100)
    private String errorCode;
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    protected DataEntryRecordChange() {}

    public static DataEntryRecordChange prepared(UUID operationLogId, UUID formId, String recordKey,
            int sequenceNo, DataEntryOperationType operationType, String operatorUsername,
            String businessKeySnapshot, String fieldSnapshot, String submittedSnapshot, String beforeSnapshot) {
        DataEntryRecordChange change = new DataEntryRecordChange();
        change.operationLogId = Objects.requireNonNull(operationLogId);
        change.formId = Objects.requireNonNull(formId);
        change.recordKey = Objects.requireNonNull(recordKey);
        change.sequenceNo = sequenceNo;
        change.operationType = Objects.requireNonNull(operationType);
        change.status = DataEntryRecordChangeStatus.PREPARED;
        change.operatorUsername = operatorUsername == null || operatorUsername.isBlank() ? "unknown" : operatorUsername.trim();
        change.businessKeySnapshot = Objects.requireNonNull(businessKeySnapshot);
        change.fieldSnapshot = Objects.requireNonNull(fieldSnapshot);
        change.submittedSnapshot = submittedSnapshot;
        change.beforeSnapshot = beforeSnapshot;
        return change;
    }

    public void succeed(String actualAfterSnapshot) {
        status = DataEntryRecordChangeStatus.SUCCEEDED;
        afterSnapshot = actualAfterSnapshot;
        completedAt = Instant.now();
        errorCode = null;
        errorMessage = null;
    }

    public void fail(String code, String message) {
        status = DataEntryRecordChangeStatus.FAILED;
        completedAt = Instant.now();
        errorCode = truncate(code, 100);
        errorMessage = truncate(message, 2000);
    }

    public void unknown(String code, String message) {
        status = DataEntryRecordChangeStatus.UNKNOWN;
        completedAt = Instant.now();
        errorCode = truncate(code, 100);
        errorMessage = truncate(message, 2000);
    }

    private static String truncate(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }

    public UUID getOperationLogId() { return operationLogId; }
    public UUID getFormId() { return formId; }
    public String getRecordKey() { return recordKey; }
    public int getSequenceNo() { return sequenceNo; }
    public DataEntryOperationType getOperationType() { return operationType; }
    public DataEntryRecordChangeStatus getStatus() { return status; }
    public String getOperatorUsername() { return operatorUsername; }
    public String getBusinessKeySnapshot() { return businessKeySnapshot; }
    public String getFieldSnapshot() { return fieldSnapshot; }
    public String getSubmittedSnapshot() { return submittedSnapshot; }
    public String getBeforeSnapshot() { return beforeSnapshot; }
    public String getAfterSnapshot() { return afterSnapshot; }
    public Instant getCompletedAt() { return completedAt; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}
