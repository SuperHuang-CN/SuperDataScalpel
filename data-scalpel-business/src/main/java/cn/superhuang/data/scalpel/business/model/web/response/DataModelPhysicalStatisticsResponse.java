package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalStatistics;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalStatisticQuality;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalStatisticsRefreshStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "最近一次人工刷新保存的模型物理表统计快照")
public record DataModelPhysicalStatisticsResponse(
        @Schema(description = "模型 UUID") UUID modelId,
        @Schema(description = "数据库系统统计提供的行数；不可获取时为空，可能是估算值") Long rowCount,
        @Schema(description = "行数质量：EXACT 精确、ESTIMATED 估算或 UNAVAILABLE 不可获取") PhysicalStatisticQuality rowCountQuality,
        @Schema(description = "数据、索引、分区及可统计附属对象的总物理占用字节数；不可获取时为空") Long storageBytes,
        @Schema(description = "占用空间质量：EXACT 精确、ESTIMATED 估算或 UNAVAILABLE 不可获取") PhysicalStatisticQuality storageQuality,
        @Schema(description = "最近一次成功采集到当前数值的时间；无成功结果时为空") Instant collectedAt,
        @Schema(description = "最近一次刷新尝试完成时间") Instant lastRefreshAt,
        @Schema(description = "最近一次刷新状态；失败会保留上一次成功数值，物理表不存在会清空旧值") PhysicalStatisticsRefreshStatus lastRefreshStatus,
        @Schema(description = "最近刷新结果的安全说明或失败原因") String message
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
