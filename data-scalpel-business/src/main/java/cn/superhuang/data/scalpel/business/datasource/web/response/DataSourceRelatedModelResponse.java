package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "storageDataSourceId 直接指向该数据源的数据模型摘要；用于评估修改或删除数据源的影响，只读取管理库且不探测物理表。")
public record DataSourceRelatedModelResponse(
        @Schema(description = "数据模型 UUID，可用于查询模型详情或处理依赖。") UUID modelId,
        @Schema(description = "模型在平台内稳定唯一的技术编码。") String modelCode,
        @Schema(description = "模型当前显示名称。") String modelName,
        @Schema(description = "模型当前生命周期：DRAFT 草稿、PUBLISHED 已发布、DISABLED 已停用。") DataModelStatus status,
        @Schema(description = "物理表管理方式：MANAGED 由平台按模型结构管理，EXTERNAL 绑定并遵循外部已有表结构。") PhysicalTableMode physicalTableMode,
        @Schema(description = "物理表 Catalog 名称；目标数据库不使用 Catalog 时为空。") String catalogName,
        @Schema(description = "物理表 Schema 名称；目标数据库不使用 Schema 时为空。") String schemaName,
        @Schema(description = "模型绑定或计划创建的物理表名称。") String physicalTableName,
        @Schema(description = "模型字段结构版本，从 1 开始；字段结构实际变化时递增，可用于判断此前读取的字段契约是否过期。") int schemaVersion,
        @Schema(description = "模型管理记录最后更新时间，ISO-8601 UTC 时间戳；不表示外部物理表最后更新时间。") Instant updatedAt
) {
    public static DataSourceRelatedModelResponse from(DataModel model) {
        return new DataSourceRelatedModelResponse(
                model.getId(), model.getCode(), model.getName(), model.getStatus(), model.getPhysicalTableMode(),
                model.getCatalogName(), model.getSchemaName(), model.getPhysicalTableName(), model.getSchemaVersion(),
                model.getUpdatedAt()
        );
    }
}
