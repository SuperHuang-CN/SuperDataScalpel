package cn.superhuang.data.scalpel.business.asset.web.response;

import cn.superhuang.data.scalpel.business.asset.domain.AssetSensitivityLevel;
import cn.superhuang.data.scalpel.business.asset.domain.AssetSyncStatus;
import cn.superhuang.data.scalpel.business.asset.domain.AssetType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssetPortalAssetSummaryResponse(
        UUID id,
        AssetType assetType,
        String name,
        String code,
        String summary,
        String directoryPath,
        List<String> tags,
        String ownerName,
        String updateFrequency,
        AssetSensitivityLevel sensitivityLevel,
        AssetSyncStatus syncStatus,
        boolean featured,
        Instant publishedAt,
        Instant sourceUpdatedAt
) {
}
