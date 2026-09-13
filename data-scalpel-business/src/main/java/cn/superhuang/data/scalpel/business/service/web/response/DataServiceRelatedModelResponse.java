package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;

import java.time.Instant;
import java.util.UUID;

/** A resolved model reference used by data-service definition, relation and lineage views. */
@Schema(description = "数据服务定义引用的模型及其存储位置快照。")
public record DataServiceRelatedModelResponse(
        @Schema(description = "模型 UUID。")
        UUID modelId,
        @Schema(description = "模型在服务定义中的角色：PRIMARY 主模型或 REFERENCE 辅助引用模型。")
        DataServiceRelatedModelRole role,
        @Schema(description = "模型在当前服务定义中的显示顺序，从 1 开始；只在当前定义版本内有意义。")
        int order,
        @Schema(description = "模型引用当前是否能解析；false 时后续名称、状态和存储字段可能为空。")
        boolean resolved,
        @Schema(description = "关联模型当前稳定编码；引用无法解析时为空。")
        String code,
        @Schema(description = "关联模型当前名称；引用无法解析时为空。")
        String name,
        @Schema(description = "关联模型当前生命周期。STANDARD_TABLE 和 SPATIAL_SERVICE 启用时要求 PUBLISHED；SQL_QUERY 允许 DRAFT、PUBLISHED 或 DISABLED，状态不参与保存、测试或启用判定。")
        DataModelStatus status,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "所属目录名称。")
        String directoryName,
        @Schema(description = "模型所属数仓分层 UUID；模型未配置分层或引用无法解析时为空。")
        UUID warehouseLayerId,
        @Schema(description = "模型所属数仓分层编码。")
        String warehouseLayerCode,
        @Schema(description = "模型所属数仓分层名称。")
        String warehouseLayerName,
        @Schema(description = "模型物理表所属 STORAGE 数据源 UUID；引用无法解析时为空。")
        UUID storageDataSourceId,
        @Schema(description = "模型存储数据源编码。")
        String storageDataSourceCode,
        @Schema(description = "模型存储数据源名称。")
        String storageDataSourceName,
        @Schema(description = "数据库 Catalog 名；数据源不支持 Catalog 时为空。")
        String catalogName,
        @Schema(description = "数据库 Schema 名；数据源不支持 Schema 时为空。")
        String schemaName,
        @Schema(description = "模型对应的物理表名。")
        String physicalTableName,
        @Schema(description = "当前模型字段结构版本，从 1 开始；字段结构变化时递增。引用无法解析时为空。")
        Integer schemaVersion,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
}
