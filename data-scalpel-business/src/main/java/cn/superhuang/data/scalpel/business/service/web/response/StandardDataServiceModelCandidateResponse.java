package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;

import java.time.Instant;
import java.util.UUID;

/** A pageable model projection tailored to standard-table service definition selection. */
@Schema(description = "标准表查询服务可选模型及其物理存储位置；selectable 表示当前能否保存为服务定义。")
public record StandardDataServiceModelCandidateResponse(
        @Schema(description = "候选数据模型 UUID。")
        UUID id,
        @Schema(description = "候选数据模型稳定编码。")
        String code,
        @Schema(description = "候选数据模型名称。")
        String name,
        @Schema(description = "模型生命周期：DRAFT 草稿，PUBLISHED 已发布可被服务稳定引用，DISABLED 已停用。")
        DataModelStatus status,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "所属目录名称。")
        String directoryName,
        @Schema(description = "模型所属数仓分层 UUID；模型未配置分层时为空。")
        UUID warehouseLayerId,
        @Schema(description = "模型所属数仓分层编码。")
        String warehouseLayerCode,
        @Schema(description = "模型所属数仓分层名称。")
        String warehouseLayerName,
        @Schema(description = "模型物理表所属 STORAGE 数据源 UUID。")
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
        @Schema(description = "当前模型字段结构版本，从 1 开始；字段结构变化时递增。")
        int schemaVersion,
        @Schema(description = "模型当前定义中的字段总数。")
        long fieldCount,
        @Schema(description = "当前模型是否满足标准服务要求并可用于保存定义。")
        boolean selectable,
        @Schema(description = "selectable=false 时的具体原因，例如模型未发布、没有 STORAGE 数据源或物理表不可用；可选择时为空。")
        String unavailableReason,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
}
