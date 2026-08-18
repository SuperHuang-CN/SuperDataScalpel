package cn.superhuang.data.scalpel.business.asset.web.response;

import java.util.List;
import java.util.UUID;

public record AssetBatchOperationResponse(
        int totalCount,
        int successCount,
        int outdatedCount,
        int unavailableCount,
        int missingCount,
        int failedCount,
        List<Failure> failures
) {
    public record Failure(UUID assetId, String assetName, String message) {
    }
}
