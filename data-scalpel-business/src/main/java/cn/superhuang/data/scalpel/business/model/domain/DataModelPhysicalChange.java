package cn.superhuang.data.scalpel.business.model.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.dialect.model.TableChangeRisk;
import cn.superhuang.data.scalpel.dialect.model.TableChangeStrategy;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import cn.superhuang.data.scalpel.dialect.model.TableDdlAtomicity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Immutable snapshots and lifecycle information for one requested physical-table change. */
@Entity
@Table(
        name = "ds_data_model_physical_change",
        indexes = {
                @Index(name = "idx_ds_model_physical_change_model_status", columnList = "model_id,status"),
                @Index(name = "idx_ds_model_physical_change_model_created", columnList = "model_id,created_at")
        }
)
public class DataModelPhysicalChange extends BaseEntity {

    @Column(name = "model_id", nullable = false, updatable = false)
    private UUID modelId;

    @Column(name = "base_schema_version", nullable = false, updatable = false)
    private int baseSchemaVersion;

    @Column(name = "target_schema_version", nullable = false, updatable = false)
    private int targetSchemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DataModelPhysicalChangeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private TableChangeStrategy strategy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private TableChangeRisk risk;

    @Enumerated(EnumType.STRING)
    @Column(name = "ddl_atomicity", nullable = false, length = 40, updatable = false)
    private TableDdlAtomicity ddlAtomicity;

    @Column(name = "before_fingerprint", nullable = false, length = 64, updatable = false)
    private String beforeFingerprint;

    @Column(name = "target_fingerprint", nullable = false, length = 64, updatable = false)
    private String targetFingerprint;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "plan_snapshot", nullable = false, updatable = false)
    private String planSnapshot;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "target_fields_snapshot", nullable = false, updatable = false)
    private String targetFieldsSnapshot;

    @Column(name = "execution_started_at")
    private Instant executionStartedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", length = 32)
    private TableChangeExecutionMode executionMode;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    protected DataModelPhysicalChange() {
    }

    private DataModelPhysicalChange(
            UUID modelId,
            int baseSchemaVersion,
            int targetSchemaVersion,
            TableChangeStrategy strategy,
            TableChangeRisk risk,
            TableDdlAtomicity ddlAtomicity,
            String beforeFingerprint,
            String targetFingerprint,
            String planSnapshot,
            String targetFieldsSnapshot
    ) {
        if (modelId == null || baseSchemaVersion < 1 || targetSchemaVersion <= baseSchemaVersion) {
            throw new IllegalArgumentException("Invalid physical table change version or model");
        }
        this.modelId = modelId;
        this.baseSchemaVersion = baseSchemaVersion;
        this.targetSchemaVersion = targetSchemaVersion;
        this.status = DataModelPhysicalChangeStatus.PLANNED;
        this.strategy = require(strategy, "Change strategy is required");
        this.risk = require(risk, "Change risk is required");
        this.ddlAtomicity = require(ddlAtomicity, "DDL atomicity is required");
        this.beforeFingerprint = requireText(beforeFingerprint, "Before fingerprint is required");
        this.targetFingerprint = requireText(targetFingerprint, "Target fingerprint is required");
        this.planSnapshot = requireText(planSnapshot, "Plan snapshot is required");
        this.targetFieldsSnapshot = requireText(targetFieldsSnapshot, "Target fields snapshot is required");
    }

    public static DataModelPhysicalChange planned(
            UUID modelId,
            int baseSchemaVersion,
            int targetSchemaVersion,
            TableChangeStrategy strategy,
            TableChangeRisk risk,
            TableDdlAtomicity ddlAtomicity,
            String beforeFingerprint,
            String targetFingerprint,
            String planSnapshot,
            String targetFieldsSnapshot
    ) {
        return new DataModelPhysicalChange(
                modelId, baseSchemaVersion, targetSchemaVersion, strategy, risk, ddlAtomicity,
                beforeFingerprint, targetFingerprint, planSnapshot, targetFieldsSnapshot
        );
    }

    public void startApplying(TableChangeExecutionMode mode) {
        requireStatus(DataModelPhysicalChangeStatus.PLANNED, "只有待执行计划可以开始执行");
        this.executionMode = require(mode, "Execution mode is required");
        status = DataModelPhysicalChangeStatus.APPLYING;
        executionStartedAt = Instant.now();
        errorCode = null;
        errorMessage = null;
    }

    public void succeed() {
        requireStatus(DataModelPhysicalChangeStatus.APPLYING, "只有执行中的计划可以标记为成功");
        status = DataModelPhysicalChangeStatus.SUCCEEDED;
        completedAt = Instant.now();
    }

    public void fail(String code, String message) {
        requireStatus(DataModelPhysicalChangeStatus.APPLYING, "只有执行中的计划可以标记为失败");
        status = DataModelPhysicalChangeStatus.FAILED;
        completeWithError(code, message);
    }

    public void markPartial(String code, String message) {
        requireStatus(DataModelPhysicalChangeStatus.APPLYING, "只有执行中的计划可以标记为部分完成");
        status = DataModelPhysicalChangeStatus.PARTIAL;
        completeWithError(code, message);
    }

    public void cancel() {
        requireStatus(DataModelPhysicalChangeStatus.PLANNED, "只有待执行计划可以取消");
        status = DataModelPhysicalChangeStatus.CANCELLED;
        completedAt = Instant.now();
    }

    public void supersede() {
        requireStatus(DataModelPhysicalChangeStatus.PLANNED, "只有待执行计划可以被替代");
        status = DataModelPhysicalChangeStatus.SUPERSEDED;
        completedAt = Instant.now();
    }

    public UUID getModelId() {
        return modelId;
    }

    public int getBaseSchemaVersion() {
        return baseSchemaVersion;
    }

    public int getTargetSchemaVersion() {
        return targetSchemaVersion;
    }

    public DataModelPhysicalChangeStatus getStatus() {
        return status;
    }

    public TableChangeStrategy getStrategy() {
        return strategy;
    }

    public TableChangeRisk getRisk() {
        return risk;
    }

    public TableDdlAtomicity getDdlAtomicity() {
        return ddlAtomicity;
    }

    public String getBeforeFingerprint() {
        return beforeFingerprint;
    }

    public String getTargetFingerprint() {
        return targetFingerprint;
    }

    public String getPlanSnapshot() {
        return planSnapshot;
    }

    public String getTargetFieldsSnapshot() {
        return targetFieldsSnapshot;
    }

    public Instant getExecutionStartedAt() {
        return executionStartedAt;
    }

    public TableChangeExecutionMode getExecutionMode() {
        return executionMode;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    private void completeWithError(String code, String message) {
        errorCode = requireText(code, "Error code is required");
        errorMessage = requireText(message, "Error message is required");
        completedAt = Instant.now();
    }

    private void requireStatus(DataModelPhysicalChangeStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
