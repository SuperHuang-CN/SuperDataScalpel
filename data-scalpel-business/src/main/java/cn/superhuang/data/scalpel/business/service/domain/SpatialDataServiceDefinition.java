package cn.superhuang.data.scalpel.business.service.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Design-time model binding for a GeoServer-backed spatial service. */
@Entity
@Table(name = "ds_spatial_data_service_definition", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_spatial_service_definition_service", columnNames = "data_service_id"
))
public class SpatialDataServiceDefinition extends BaseEntity {

    @Column(name = "data_service_id", nullable = false, updatable = false)
    private UUID dataServiceId;

    @Column(name = "model_id", nullable = false)
    private UUID modelId;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "style_mode", length = 24)
    private SpatialStyleMode styleMode;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "simple_style_json")
    private String simpleStyleJson;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "style_document_json")
    private String styleDocumentJson;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "uploaded_sld_text")
    private String uploadedSldText;

    @Column(name = "sld_file_name", length = 255)
    private String sldFileName;

    @Column(name = "style_version")
    private Integer styleVersion;

    @Column(name = "applied_style_version")
    private Integer appliedStyleVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "style_sync_status", length = 24)
    private SpatialStyleSyncStatus styleSyncStatus;

    @Column(name = "style_sync_error", length = 1000)
    private String styleSyncError;

    @Column(name = "style_applied_at")
    private Instant styleAppliedAt;

    protected SpatialDataServiceDefinition() {
    }

    private SpatialDataServiceDefinition(UUID dataServiceId, UUID modelId) {
        this.dataServiceId = Objects.requireNonNull(dataServiceId, "数据服务不能为空");
        this.modelId = Objects.requireNonNull(modelId, "空间模型不能为空");
        this.version = 1;
    }

    public static SpatialDataServiceDefinition create(UUID dataServiceId, UUID modelId) {
        return new SpatialDataServiceDefinition(dataServiceId, modelId);
    }

    public void update(UUID modelId) {
        UUID next = Objects.requireNonNull(modelId, "空间模型不能为空");
        if (!next.equals(this.modelId)) {
            this.modelId = next;
            this.version++;
            resetStyle();
        }
    }

    public void saveSimpleStyle(String simpleStyleJson, boolean deployed) {
        this.styleMode = SpatialStyleMode.SIMPLE;
        this.simpleStyleJson = required(simpleStyleJson, "简单样式配置");
        advanceStyleVersion(deployed);
    }

    public boolean saveStyleDocument(String styleDocumentJson, boolean deployed) {
        String normalized = required(styleDocumentJson, "在线制图样式文档");
        if (getStyleMode() == SpatialStyleMode.CARTOGRAPHY && normalized.equals(this.styleDocumentJson)) {
            return false;
        }
        this.styleMode = SpatialStyleMode.CARTOGRAPHY;
        this.styleDocumentJson = normalized;
        advanceStyleVersion(deployed);
        return true;
    }

    /** Replaces a legacy online draft without disturbing an active uploaded SLD. */
    public void resetCartographyDocument(String styleDocumentJson) {
        this.styleDocumentJson = required(styleDocumentJson, "在线制图样式文档");
        this.simpleStyleJson = null;
        if (getStyleMode() == SpatialStyleMode.UPLOADED_SLD) {
            return;
        }
        this.styleMode = SpatialStyleMode.CARTOGRAPHY;
        this.styleVersion = getStyleVersion() + 1;
        this.styleSyncStatus = appliedStyleVersion == null
                ? SpatialStyleSyncStatus.NOT_APPLIED
                : SpatialStyleSyncStatus.OUT_OF_SYNC;
        this.styleSyncError = null;
    }

    /** Initializes the current document for a new definition or model-family reset without another version bump. */
    public void initializeCartographyDocument(String styleDocumentJson) {
        this.styleMode = SpatialStyleMode.CARTOGRAPHY;
        this.styleDocumentJson = required(styleDocumentJson, "在线制图样式文档");
        this.simpleStyleJson = null;
    }

    public void saveUploadedSld(String fileName, String sldText, boolean deployed) {
        this.styleMode = SpatialStyleMode.UPLOADED_SLD;
        this.uploadedSldText = required(sldText, "SLD内容");
        this.sldFileName = required(fileName, "SLD文件名");
        advanceStyleVersion(deployed);
    }

    public boolean activateUploadedSld(boolean deployed) {
        if (uploadedSldText == null || uploadedSldText.isBlank()) {
            throw new IllegalStateException("尚未保存可重新激活的上传 SLD");
        }
        if (getStyleMode() == SpatialStyleMode.UPLOADED_SLD) return false;
        this.styleMode = SpatialStyleMode.UPLOADED_SLD;
        advanceStyleVersion(deployed);
        return true;
    }

    public void beginStyleSync() {
        this.styleSyncStatus = SpatialStyleSyncStatus.SYNCING;
        this.styleSyncError = null;
    }

    public void completeStyleSync(int appliedVersion) {
        this.appliedStyleVersion = appliedVersion;
        this.styleAppliedAt = Instant.now();
        this.styleSyncError = null;
        this.styleSyncStatus = getStyleVersion() == appliedVersion
                ? SpatialStyleSyncStatus.IN_SYNC
                : SpatialStyleSyncStatus.OUT_OF_SYNC;
    }

    public void failStyleSync(int attemptedVersion, String error) {
        this.styleSyncError = optionalError(error);
        this.styleSyncStatus = getStyleVersion() == attemptedVersion
                ? SpatialStyleSyncStatus.SYNC_FAILED
                : SpatialStyleSyncStatus.OUT_OF_SYNC;
    }

    public void styleRemoved() {
        this.appliedStyleVersion = null;
        this.styleAppliedAt = null;
        this.styleSyncError = null;
        this.styleSyncStatus = SpatialStyleSyncStatus.NOT_APPLIED;
    }

    private void resetStyle() {
        this.styleMode = SpatialStyleMode.CARTOGRAPHY;
        this.simpleStyleJson = null;
        this.styleDocumentJson = null;
        this.uploadedSldText = null;
        this.sldFileName = null;
        this.styleVersion = getStyleVersion() + 1;
        this.appliedStyleVersion = null;
        this.styleAppliedAt = null;
        this.styleSyncError = null;
        this.styleSyncStatus = SpatialStyleSyncStatus.NOT_APPLIED;
    }

    private void advanceStyleVersion(boolean deployed) {
        this.styleVersion = getStyleVersion() + 1;
        this.styleSyncStatus = deployed
                ? SpatialStyleSyncStatus.OUT_OF_SYNC
                : SpatialStyleSyncStatus.NOT_APPLIED;
        this.styleSyncError = null;
    }

    public UUID getDataServiceId() {
        return dataServiceId;
    }

    public UUID getModelId() {
        return modelId;
    }

    public int getVersion() {
        return version;
    }

    public SpatialStyleMode getStyleMode() {
        return styleMode == null ? SpatialStyleMode.SIMPLE : styleMode;
    }

    public String getSimpleStyleJson() {
        return simpleStyleJson;
    }

    public String getStyleDocumentJson() {
        return styleDocumentJson;
    }

    public String getUploadedSldText() {
        return uploadedSldText;
    }

    public String getSldFileName() {
        return sldFileName;
    }

    public int getStyleVersion() {
        return styleVersion == null ? 0 : styleVersion;
    }

    public Integer getAppliedStyleVersion() {
        return appliedStyleVersion;
    }

    public SpatialStyleSyncStatus getStyleSyncStatus() {
        return styleSyncStatus == null ? SpatialStyleSyncStatus.NOT_APPLIED : styleSyncStatus;
    }

    public String getStyleSyncError() {
        return styleSyncError;
    }

    public Instant getStyleAppliedAt() {
        return styleAppliedAt;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }

    private static String optionalError(String value) {
        if (value == null || value.isBlank()) return "GeoServer样式同步失败";
        return value.substring(0, Math.min(1000, value.length()));
    }
}
