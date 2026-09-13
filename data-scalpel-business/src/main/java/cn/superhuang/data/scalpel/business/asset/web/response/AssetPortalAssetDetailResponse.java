package cn.superhuang.data.scalpel.business.asset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.asset.domain.AssetSensitivityLevel;
import cn.superhuang.data.scalpel.business.asset.domain.AssetSyncStatus;
import cn.superhuang.data.scalpel.business.asset.domain.AssetType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "资产门户可公开展示的资产快照、来源状态和类型专属元数据。")

public record AssetPortalAssetDetailResponse(
        @Schema(description = "已发布资产登记 UUID。")
        UUID id,
        @Schema(description = "资产来源类型：DATA_MODEL 数据模型、FILE_DATASET 文件数据集、PANORAMA 全景影像、DICTIONARY 码表、DATA_SERVICE 数据服务。")
        AssetType assetType,
        @Schema(description = "门户展示的资产名称。")
        String name,
        @Schema(description = "门户内稳定唯一的资产技术编码。")
        String code,
        @Schema(description = "面向用户的摘要说明。")
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
        @Schema(description = "本次发布发生时间，ISO-8601 UTC 时间戳；此公开详情只返回 PUBLISHED 资产，因此正常情况下不为空。")
        Instant publishedAt,
        @Schema(description = "本次详情读取所用来源数据状态。AVAILABLE 与 UNAVAILABLE 都来自实时读取，区别是来源是否满足登记条件；CACHED 表示来源缺失或读取失败并使用最近成功快照。")
        AssetPortalSourceDisplayStatus sourceDisplayStatus,
        @Schema(description = "来源资源名称。")
        String sourceName,
        @Schema(description = "来源资源稳定编码。")
        String sourceCode,
        @Schema(description = "来源资源说明。")
        String sourceDescription,
        @Schema(description = "本次用于展示的来源业务状态文本；AVAILABLE 或 UNAVAILABLE 时来自实时来源，CACHED 时来自最近成功快照，取值集合随 assetType 变化。")
        String sourceStatus,
        @Schema(description = "本次用于展示的来源更新时间，ISO-8601 UTC 时间戳；CACHED 时为快照记录值，来源未提供时为空。")
        Instant sourceUpdatedAt,
        @Schema(description = "来源业务元数据：模型包含数仓分层、Schema 版本和字段数，文件数据集包含格式及文件/表数，全景包含尺寸与拍摄信息，字典包含值类型和内容版本，数据服务包含类型、定义版本、部署及路由信息。")
        Map<String, Object> metadata
) {
}
