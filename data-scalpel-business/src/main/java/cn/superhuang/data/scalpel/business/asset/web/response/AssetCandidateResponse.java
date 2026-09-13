package cn.superhuang.data.scalpel.business.asset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.asset.domain.AssetType;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "可登记为资产的来源业务资源及当前注册资格。")

public record AssetCandidateResponse(
        @Schema(description = "资产来源类型：DATA_MODEL 数据模型、FILE_DATASET 文件数据集、PANORAMA 全景影像、DICTIONARY 码表、DATA_SERVICE 数据服务。")
        AssetType assetType,
        @Schema(description = "来源业务资源 UUID。")
        UUID resourceId,
        @Schema(description = "来源资源当前显示名称。")
        String name,
        @Schema(description = "来源资源稳定技术编码。")
        String code,
        @Schema(description = "来源资源当前说明；未填写时为空。")
        String description,
        @Schema(description = "来源类型自身的当前业务状态文本，例如模型 PUBLISHED、码表 ENABLED 或数据服务 ENABLED；不同 assetType 的取值集合不同。")
        String sourceStatus,
        @Schema(description = "来源业务资源最后更新时间，ISO-8601 UTC 时间戳；来源未提供时为空。")
        Instant sourceUpdatedAt,
        @Schema(description = "当前来源是否满足该 assetType 的登记条件；该结果只是查询时快照，登记接口会重新读取并校验。")
        boolean eligible,
        @Schema(description = "不满足注册条件的原因；eligible 为 true 时为空。")
        String ineligibleReason,
        @Schema(description = "来源是否已经注册为资产。")
        boolean registered,
        @Schema(description = "来源已注册时对应的资产 UUID；registered 为 false 时为空。")
        UUID assetId
) {
}
