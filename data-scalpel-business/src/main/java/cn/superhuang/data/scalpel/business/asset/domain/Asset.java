package cn.superhuang.data.scalpel.business.asset.domain;

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
import java.util.UUID;

@Entity
@Table(
        name = "ds_asset",
        indexes = {
                @Index(name = "idx_ds_asset_directory", columnList = "directory_id"),
                @Index(name = "idx_ds_asset_status", columnList = "status,sync_status"),
                @Index(name = "idx_ds_asset_updated", columnList = "updated_at")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_asset_source",
                columnNames = {"asset_type", "resource_id"}
        )
)
public class Asset extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, updatable = false, length = 32)
    private AssetType assetType;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(name = "directory_id")
    private UUID directoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AssetStatus status;

    @Column(name = "portal_name", length = 100)
    private String portalName;

    @Column(name = "portal_summary", length = 1000)
    private String portalSummary;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "tags_json", nullable = false)
    private String tagsJson;

    @Column(name = "owner_name", length = 100)
    private String ownerName;

    @Column(name = "update_frequency", length = 100)
    private String updateFrequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensitivity_level", length = 32)
    private AssetSensitivityLevel sensitivityLevel;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "offline_at")
    private Instant offlineAt;

    @Column(name = "source_name", nullable = false, length = 100)
    private String sourceName;

    @Column(name = "source_code", length = 100)
    private String sourceCode;

    @Column(name = "source_description", length = 1000)
    private String sourceDescription;

    @Column(name = "source_status", nullable = false, length = 64)
    private String sourceStatus;

    @Column(name = "source_updated_at")
    private Instant sourceUpdatedAt;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "source_snapshot", nullable = false)
    private String sourceSnapshot;

    @Column(name = "source_fingerprint", nullable = false, length = 64)
    private String sourceFingerprint;

    @Column(name = "last_checked_at", nullable = false)
    private Instant lastCheckedAt;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 32)
    private AssetSyncStatus syncStatus;

    @Column(name = "sync_error", length = 1000)
    private String syncError;

    protected Asset() {
    }

    private Asset(AssetType assetType, UUID resourceId) {
        this.assetType = assetType;
        this.resourceId = resourceId;
        this.status = AssetStatus.DRAFT;
        this.tagsJson = "[]";
    }

    public static Asset register(AssetType assetType, UUID resourceId) {
        if (assetType == null || resourceId == null) {
            throw new IllegalArgumentException("资产来源不能为空");
        }
        return new Asset(assetType, resourceId);
    }

    public void updatePortal(
            UUID directoryId,
            String portalName,
            String portalSummary,
            String tagsJson,
            String ownerName,
            String updateFrequency,
            AssetSensitivityLevel sensitivityLevel,
            boolean featured
    ) {
        this.directoryId = directoryId;
        this.portalName = normalizeOptional(portalName);
        this.portalSummary = normalizeOptional(portalSummary);
        this.tagsJson = tagsJson;
        this.ownerName = normalizeOptional(ownerName);
        this.updateFrequency = normalizeOptional(updateFrequency);
        this.sensitivityLevel = sensitivityLevel;
        this.featured = featured;
    }

    public void synchronizeSource(
            String sourceName,
            String sourceCode,
            String sourceDescription,
            String sourceStatus,
            Instant sourceUpdatedAt,
            String sourceSnapshot,
            String sourceFingerprint,
            Instant checkedAt
    ) {
        this.sourceName = requireText(sourceName, "来源名称不能为空");
        this.sourceCode = normalizeOptional(sourceCode);
        this.sourceDescription = normalizeOptional(sourceDescription);
        this.sourceStatus = requireText(sourceStatus, "来源状态不能为空");
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.sourceSnapshot = requireText(sourceSnapshot, "来源快照不能为空");
        this.sourceFingerprint = requireText(sourceFingerprint, "来源指纹不能为空");
        this.lastCheckedAt = checkedAt;
        this.lastSyncedAt = checkedAt;
        this.syncStatus = AssetSyncStatus.IN_SYNC;
        this.syncError = null;
    }

    public void recordCheck(AssetSyncStatus syncStatus, String syncError, Instant checkedAt) {
        this.syncStatus = syncStatus;
        this.syncError = truncate(normalizeOptional(syncError), 1000);
        this.lastCheckedAt = checkedAt;
    }

    public void publish(Instant publishedAt) {
        if (status == AssetStatus.PUBLISHED) {
            throw new IllegalStateException("资产已经发布");
        }
        status = AssetStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        offlineAt = null;
    }

    public void offline(Instant offlineAt) {
        if (status != AssetStatus.PUBLISHED) {
            throw new IllegalStateException("只有已发布资产可以下线");
        }
        status = AssetStatus.OFFLINE;
        this.offlineAt = offlineAt;
    }

    public String effectiveName() {
        return portalName == null ? sourceName : portalName;
    }

    public String effectiveSummary() {
        return portalSummary == null ? sourceDescription : portalSummary;
    }

    private static String requireText(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) throw new IllegalArgumentException(message);
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String truncate(String value, int maximumLength) {
        return value == null || value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    public AssetType getAssetType() { return assetType; }
    public UUID getResourceId() { return resourceId; }
    public UUID getDirectoryId() { return directoryId; }
    public AssetStatus getStatus() { return status; }
    public String getPortalName() { return portalName; }
    public String getPortalSummary() { return portalSummary; }
    public String getTagsJson() { return tagsJson; }
    public String getOwnerName() { return ownerName; }
    public String getUpdateFrequency() { return updateFrequency; }
    public AssetSensitivityLevel getSensitivityLevel() { return sensitivityLevel; }
    public boolean isFeatured() { return featured; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getOfflineAt() { return offlineAt; }
    public String getSourceName() { return sourceName; }
    public String getSourceCode() { return sourceCode; }
    public String getSourceDescription() { return sourceDescription; }
    public String getSourceStatus() { return sourceStatus; }
    public Instant getSourceUpdatedAt() { return sourceUpdatedAt; }
    public String getSourceSnapshot() { return sourceSnapshot; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public AssetSyncStatus getSyncStatus() { return syncStatus; }
    public String getSyncError() { return syncError; }
}
