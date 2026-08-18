package cn.superhuang.data.scalpel.business.model.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.dialect.model.TablePhysicalStatistics;
import cn.superhuang.data.scalpel.dialect.model.TableStatisticQuality;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "ds_data_model_physical_statistics",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_model_physical_statistics_model",
                columnNames = "model_id"
        )
)
public class DataModelPhysicalStatistics extends BaseEntity {

    @Column(name = "model_id", nullable = false, updatable = false)
    private UUID modelId;

    @Column(name = "row_count")
    private Long rowCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "row_count_quality", nullable = false, length = 32)
    private PhysicalStatisticQuality rowCountQuality = PhysicalStatisticQuality.UNAVAILABLE;

    @Column(name = "storage_bytes")
    private Long storageBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_quality", nullable = false, length = 32)
    private PhysicalStatisticQuality storageQuality = PhysicalStatisticQuality.UNAVAILABLE;

    @Column(name = "collected_at")
    private Instant collectedAt;

    @Column(name = "last_refresh_at", nullable = false)
    private Instant lastRefreshAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_refresh_status", nullable = false, length = 32)
    private PhysicalStatisticsRefreshStatus lastRefreshStatus;

    @Column(length = 500)
    private String message;

    protected DataModelPhysicalStatistics() {
    }

    private DataModelPhysicalStatistics(UUID modelId) {
        this.modelId = modelId;
    }

    public static DataModelPhysicalStatistics create(UUID modelId) {
        if (modelId == null) {
            throw new IllegalArgumentException("Model id is required");
        }
        return new DataModelPhysicalStatistics(modelId);
    }

    public void collect(TablePhysicalStatistics statistics, Instant refreshedAt) {
        switch (statistics.state()) {
            case NOT_FOUND -> clear(PhysicalStatisticsRefreshStatus.NOT_FOUND, refreshedAt, statistics.message());
            case UNSUPPORTED -> clear(PhysicalStatisticsRefreshStatus.UNSUPPORTED, refreshedAt, statistics.message());
            case AVAILABLE -> {
                if (statistics.rowCount() == null && statistics.storageBytes() == null) {
                    clear(PhysicalStatisticsRefreshStatus.UNSUPPORTED, refreshedAt, statistics.message());
                    return;
                }
                rowCount = statistics.rowCount();
                rowCountQuality = quality(statistics.rowCountQuality());
                storageBytes = statistics.storageBytes();
                storageQuality = quality(statistics.storageQuality());
                collectedAt = refreshedAt;
                lastRefreshAt = refreshedAt;
                if (rowCount != null && storageBytes != null) {
                    lastRefreshStatus = PhysicalStatisticsRefreshStatus.SUCCESS;
                } else {
                    lastRefreshStatus = PhysicalStatisticsRefreshStatus.PARTIAL;
                }
                message = normalizeMessage(statistics.message());
            }
        }
    }

    public void fail(Instant refreshedAt, String failureMessage) {
        lastRefreshAt = refreshedAt;
        lastRefreshStatus = PhysicalStatisticsRefreshStatus.FAILED;
        message = normalizeMessage(failureMessage);
    }

    private void clear(PhysicalStatisticsRefreshStatus status, Instant refreshedAt, String statusMessage) {
        rowCount = null;
        rowCountQuality = PhysicalStatisticQuality.UNAVAILABLE;
        storageBytes = null;
        storageQuality = PhysicalStatisticQuality.UNAVAILABLE;
        collectedAt = null;
        lastRefreshAt = refreshedAt;
        lastRefreshStatus = status;
        message = normalizeMessage(statusMessage);
    }

    private static PhysicalStatisticQuality quality(TableStatisticQuality quality) {
        return switch (quality) {
            case EXACT -> PhysicalStatisticQuality.EXACT;
            case ESTIMATED -> PhysicalStatisticQuality.ESTIMATED;
            case UNAVAILABLE -> PhysicalStatisticQuality.UNAVAILABLE;
        };
    }

    private static String normalizeMessage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    public UUID getModelId() {
        return modelId;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public PhysicalStatisticQuality getRowCountQuality() {
        return rowCountQuality;
    }

    public Long getStorageBytes() {
        return storageBytes;
    }

    public PhysicalStatisticQuality getStorageQuality() {
        return storageQuality;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public Instant getLastRefreshAt() {
        return lastRefreshAt;
    }

    public PhysicalStatisticsRefreshStatus getLastRefreshStatus() {
        return lastRefreshStatus;
    }

    public String getMessage() {
        return message;
    }
}
