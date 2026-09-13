package cn.superhuang.data.scalpel.business.asset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.asset.domain.AssetSensitivityLevel;
import cn.superhuang.data.scalpel.business.asset.domain.AssetSyncStatus;
import cn.superhuang.data.scalpel.business.asset.domain.AssetType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "资产门户列表使用的已发布资产摘要和来源同步状态。")

public record AssetPortalAssetSummaryResponse(
        @Schema(description = "已发布资产登记 UUID。")
        UUID id,
        @Schema(description = "资产来源类型：DATA_MODEL 数据模型、FILE_DATASET 文件数据集、PANORAMA 全景影像、DICTIONARY 码表、DATA_SERVICE 数据服务。")
        AssetType assetType,
        @Schema(description = "门户最终展示的资产名称。")
        String name,
        @Schema(description = "门户内稳定唯一的资产技术编码。")
        String code,
        @Schema(description = "门户最终展示的资产摘要。")
        String summary,
        @Schema(description = "从根目录到资源的目录路径。")
        String directoryPath,
        @Schema(description = "用于检索和展示的标签集合。")
        List<String> tags,
        @Schema(description = "业务负责人名称。")
        String ownerName,
        @Schema(description = "数据更新频率的展示说明。")
        String updateFrequency,
        @Schema(description = "数据敏感级别：PUBLIC 公开、INTERNAL 内部使用、SENSITIVE 敏感。")
        AssetSensitivityLevel sensitivityLevel,
        @Schema(description = "资产快照与来源的同步状态：IN_SYNC 同步，OUTDATED 过期，SOURCE_UNAVAILABLE 来源不可用，SOURCE_MISSING 来源缺失，FAILED 检查失败。")
        AssetSyncStatus syncStatus,
        @Schema(description = "是否在资产门户中重点展示。")
        boolean featured,
        @Schema(description = "本次发布发生时间，ISO-8601 UTC 时间戳；公开列表只返回 PUBLISHED 资产，因此正常情况下不为空。")
        Instant publishedAt,
        @Schema(description = "最近一次成功同步时保存的来源更新时间，ISO-8601 UTC 时间戳；可能落后于实时来源，来源未提供时为空。")
        Instant sourceUpdatedAt
) {
}
