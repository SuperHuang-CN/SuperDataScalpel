package cn.superhuang.data.scalpel.business.asset.web.response;

import cn.superhuang.data.scalpel.business.asset.domain.AssetType;

import java.time.Instant;
import java.util.UUID;

public record AssetCandidateResponse(
        AssetType assetType,
        UUID resourceId,
        String name,
        String code,
        String description,
        String sourceStatus,
        Instant sourceUpdatedAt,
        boolean eligible,
        String ineligibleReason,
        boolean registered,
        UUID assetId
) {
}
