package cn.superhuang.data.scalpel.business.asset.web.response;

import cn.superhuang.data.scalpel.business.asset.domain.AssetType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AssetPortalOverviewResponse(
        long totalCount,
        Map<AssetType, Long> typeCounts,
        List<String> popularTags,
        List<Domain> domains
) {
    public record Domain(
            UUID id,
            String name,
            String description,
            long assetCount
    ) {
    }
}
