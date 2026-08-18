package cn.superhuang.data.scalpel.business.asset.service;

import cn.superhuang.data.scalpel.business.asset.domain.AssetType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

record AssetSourceSnapshot(
        AssetType assetType,
        UUID resourceId,
        String name,
        String code,
        String description,
        String sourceStatus,
        Instant sourceUpdatedAt,
        boolean available,
        String unavailableReason,
        Map<String, Object> metadata,
        String json,
        String fingerprint
) {
}
