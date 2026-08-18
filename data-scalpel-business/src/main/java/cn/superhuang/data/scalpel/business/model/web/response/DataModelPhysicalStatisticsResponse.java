package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalStatistics;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalStatisticQuality;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalStatisticsRefreshStatus;

import java.time.Instant;
import java.util.UUID;

public record DataModelPhysicalStatisticsResponse(
        UUID modelId,
        Long rowCount,
        PhysicalStatisticQuality rowCountQuality,
        Long storageBytes,
        PhysicalStatisticQuality storageQuality,
        Instant collectedAt,
        Instant lastRefreshAt,
        PhysicalStatisticsRefreshStatus lastRefreshStatus,
        String message
) {
    public static DataModelPhysicalStatisticsResponse from(DataModelPhysicalStatistics statistics) {
        return new DataModelPhysicalStatisticsResponse(
                statistics.getModelId(),
                statistics.getRowCount(),
                statistics.getRowCountQuality(),
                statistics.getStorageBytes(),
                statistics.getStorageQuality(),
                statistics.getCollectedAt(),
                statistics.getLastRefreshAt(),
                statistics.getLastRefreshStatus(),
                statistics.getMessage()
        );
    }
}
