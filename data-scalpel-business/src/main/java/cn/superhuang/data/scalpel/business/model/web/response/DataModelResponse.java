package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "数据模型基础信息、物理位置和最近物理统计快照")
public record DataModelResponse(
        @Schema(description = "模型 UUID") UUID id,
        @Schema(description = "全局唯一且创建后不可修改的模型编码") String code,
        @Schema(description = "模型显示名称") String name,
        @Schema(description = "所属 MODEL 范围目录 UUID；未分类时为空") UUID directoryId,
        @Schema(description = "数仓分层摘要；未分层时为空") ModelWarehouseLayerSummaryResponse warehouseLayer,
        @Schema(description = "绑定的 JDBC 数据源 UUID") UUID storageDataSourceId,
        @Schema(description = "绑定数据源当前显示名称") String storageDataSourceName,
        @Schema(description = "创建或绑定时解析并保存的物理 Catalog；数据库不使用 Catalog 时为空") String catalogName,
        @Schema(description = "创建或绑定时解析并保存的物理 Schema；数据库不使用 Schema 时为空") String schemaName,
        @Schema(description = "目标或已绑定的物理表名") String physicalTableName,
        @Schema(description = "MANAGED 平台受控物理表或 EXTERNAL 外部已有物理表") PhysicalTableMode physicalTableMode,
        @Schema(description = "单机 ClickHouse MergeTree 排序键字段编码；其他数据库为空列表") List<String> clickHouseOrderByColumns,
        @Schema(description = "模型生命周期状态：草稿、已发布或已停用") DataModelStatus status,
        @Schema(description = "模型字段快照版本，创建时为 1；每次成功整体保存字段或完成物理表变更计划都会递增，即使只修改字段名称、说明、顺序、码表绑定或提交相同内容。修改模型基础资料不递增。") int schemaVersion,
        @Schema(description = "最近一次人工采集的物理表统计快照；从未刷新时为空") DataModelPhysicalStatisticsResponse physicalStatistics,
        @Schema(description = "模型业务含义、粒度、更新口径或使用说明") String description,
        @Schema(description = "模型创建时间") Instant createdAt,
        @Schema(description = "模型元数据最后更新时间；刷新物理统计不会改变该时间") Instant updatedAt
) {
    public static DataModelResponse from(
            DataModel model,
            String storageDataSourceName,
            ModelWarehouseLayerSummaryResponse warehouseLayer,
            DataModelPhysicalStatisticsResponse physicalStatistics
    ) {
        return new DataModelResponse(
                model.getId(), model.getCode(), model.getName(), model.getDirectoryId(),
                warehouseLayer,
                model.getStorageDataSourceId(), storageDataSourceName, model.getCatalogName(), model.getSchemaName(),
                model.getPhysicalTableName(), model.getPhysicalTableMode(), model.getClickHouseOrderByColumns(), model.getStatus(), model.getSchemaVersion(), physicalStatistics, model.getDescription(),
                model.getCreatedAt(), model.getUpdatedAt()
        );
    }

    public static DataModelResponse from(
            DataModel model,
            String storageDataSourceName,
            ModelWarehouseLayerSummaryResponse warehouseLayer
    ) {
        return from(model, storageDataSourceName, warehouseLayer, null);
    }

    public static DataModelResponse from(DataModel model, String storageDataSourceName) {
        return from(model, storageDataSourceName, null, null);
    }
}
