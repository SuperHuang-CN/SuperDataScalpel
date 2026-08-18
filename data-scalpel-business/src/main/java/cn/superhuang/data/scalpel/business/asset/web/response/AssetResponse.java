package cn.superhuang.data.scalpel.business.asset.web.response;

import cn.superhuang.data.scalpel.business.asset.domain.AssetSensitivityLevel;
import cn.superhuang.data.scalpel.business.asset.domain.AssetStatus;
import cn.superhuang.data.scalpel.business.asset.domain.AssetSyncStatus;
import cn.superhuang.data.scalpel.business.asset.domain.AssetType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AssetResponse(
        UUID id,
        AssetType assetType,
        UUID resourceId,
        UUID directoryId,
        AssetStatus status,
        String effectiveName,
        String effectiveSummary,
        String portalName,
        String portalSummary,
        List<String> tags,
        String ownerName,
        String updateFrequency,
        AssetSensitivityLevel sensitivityLevel,
        boolean featured,
        Instant publishedAt,
        Instant offlineAt,
        String sourceName,
        String sourceCode,
        String sourceDescription,
        String sourceStatus,
        Instant sourceUpdatedAt,
        Map<String, Object> sourceSnapshot,
        Instant lastCheckedAt,
        Instant lastSyncedAt,
        AssetSyncStatus syncStatus,
        String syncError,
        Instant createdAt,
        Instant updatedAt
) {
}
