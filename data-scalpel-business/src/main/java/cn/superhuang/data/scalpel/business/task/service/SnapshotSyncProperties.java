package cn.superhuang.data.scalpel.business.task.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "data-scalpel.task.snapshot-sync")
public record SnapshotSyncProperties(
        @Min(1) int maxRowsPerSide,
        @Min(1) long maxEstimatedBytes,
        @Min(1) int lockTimeoutSeconds
) {
    public CanvasTaskRunManifest.SnapshotSyncLimits toManifestLimits() {
        return new CanvasTaskRunManifest.SnapshotSyncLimits(
                maxRowsPerSide, maxEstimatedBytes, lockTimeoutSeconds);
    }
}
