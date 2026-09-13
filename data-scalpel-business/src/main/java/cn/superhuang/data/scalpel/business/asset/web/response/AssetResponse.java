package cn.superhuang.data.scalpel.business.asset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.asset.domain.AssetSensitivityLevel;
import cn.superhuang.data.scalpel.business.asset.domain.AssetStatus;
import cn.superhuang.data.scalpel.business.asset.domain.AssetSyncStatus;
import cn.superhuang.data.scalpel.business.asset.domain.AssetType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "资产登记、门户覆盖值、来源快照和同步健康状态的管理详情。")

public record AssetResponse(
        @Schema(description = "资产登记 UUID。")
        UUID id,
        @Schema(description = "资产来源类型：DATA_MODEL 数据模型、FILE_DATASET 文件数据集、PANORAMA 全景影像、DICTIONARY 码表、DATA_SERVICE 数据服务。")
        AssetType assetType,
        @Schema(description = "来源业务资源 UUID。")
        UUID resourceId,
        @Schema(description = "ASSET 范围业务领域目录 UUID；为空表示未分类。目录只用于门户分类，不构成权限或数据隔离。")
        UUID directoryId,
        @Schema(description = "资产生命周期：DRAFT 草稿，PUBLISHED 已发布到门户，OFFLINE 已下线。")
        AssetStatus status,
        @Schema(description = "资产最终展示名称，优先使用门户覆盖值。")
        String effectiveName,
        @Schema(description = "资产最终展示摘要，优先使用门户覆盖值。")
        String effectiveSummary,
        @Schema(description = "资产门户展示名称；为空时使用来源名称。")
        String portalName,
        @Schema(description = "资产门户展示摘要；为空时使用来源说明。")
        String portalSummary,
        @Schema(description = "用于检索和展示的标签集合。")
        List<String> tags,
        @Schema(description = "业务负责人名称。")
        String ownerName,
        @Schema(description = "数据更新频率的展示说明。")
        String updateFrequency,
        @Schema(description = "数据敏感级别：PUBLIC 公开、INTERNAL 内部使用、SENSITIVE 敏感。")
        AssetSensitivityLevel sensitivityLevel,
        @Schema(description = "是否在资产门户中重点展示。")
        boolean featured,
        @Schema(description = "发布时间；尚未发布时为空。")
        Instant publishedAt,
        @Schema(description = "下线时间；尚未发生时为空。")
        Instant offlineAt,
        @Schema(description = "最近一次成功同步时保存的来源名称；可能落后于当前来源。")
        String sourceName,
        @Schema(description = "最近一次成功同步时保存的来源稳定技术编码；来源未定义编码时为空。")
        String sourceCode,
        @Schema(description = "最近一次成功同步时保存的来源说明；来源未填写时为空。")
        String sourceDescription,
        @Schema(description = "最近一次成功同步时保存的来源业务状态文本；取值集合随 assetType 变化，不一定代表来源现在的实时状态。")
        String sourceStatus,
        @Schema(description = "最近一次成功同步时记录的来源更新时间，ISO-8601 UTC 时间戳；来源未提供时为空。")
        Instant sourceUpdatedAt,
        @Schema(description = "最近一次成功同步的来源快照，包含 assetType、resourceId、name、code、description、sourceStatus、sourceUpdatedAt 和按资产类型变化的 metadata。")
        Map<String, Object> sourceSnapshot,
        @Schema(description = "最近一次检查或同步尝试完成的时间，ISO-8601 UTC 时间戳。登记时已完成首次同步，因此正常资产不为空。")
        Instant lastCheckedAt,
        @Schema(description = "最近一次成功覆盖来源快照的时间，ISO-8601 UTC 时间戳。来源不可用、缺失或读取失败的同步尝试不会更新该值。")
        Instant lastSyncedAt,
        @Schema(description = "资产快照与来源的同步状态：IN_SYNC 同步，OUTDATED 过期，SOURCE_UNAVAILABLE 来源不可用，SOURCE_MISSING 来源缺失，FAILED 检查失败。")
        AssetSyncStatus syncStatus,
        @Schema(description = "最近一次来源检查或同步失败的安全错误说明；没有错误时为空。")
        String syncError,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
}
