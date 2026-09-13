package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "空间数据服务可选模型及其物理空间表映射；selectable 表示当前能否保存为服务定义。")

public record SpatialDataServiceModelCandidateResponse(
        @Schema(description = "候选数据模型 UUID。")
        UUID id,
        @Schema(description = "候选数据模型稳定编码。")
        String code,
        @Schema(description = "候选数据模型名称。")
        String name,
        @Schema(description = "模型生命周期：DRAFT 草稿，PUBLISHED 已发布可被服务稳定引用，DISABLED 已停用。")
        DataModelStatus status,
        @Schema(description = "数据源 UUID。")
        UUID dataSourceId,
        @Schema(description = "数据源稳定编码。")
        String dataSourceCode,
        @Schema(description = "数据源名称。")
        String dataSourceName,
        @Schema(description = "物理空间表所在数据库 Catalog；数据源不支持 Catalog 时为空。")
        String catalog,
        @Schema(description = "物理空间表所在数据库 Schema；数据源不支持 Schema 时为空。")
        String schema,
        @Schema(description = "模型绑定的物理表名称。")
        String table,
        @Schema(description = "唯一可发布 Geometry 字段对应的物理列名。")
        String geometryColumn,
        @Schema(description = "空间字段的精确几何类型；必须是服务引擎支持的点、线、面或通用 Geometry。")
        GeometryKind geometryKind,
        @Schema(description = "坐标参考系 EPSG 数字编码。")
        Integer epsg,
        @Schema(description = "用于 WFS 要素标识的唯一非 Geometry 主键物理列。")
        String primaryKeyColumn,
        @Schema(description = "当前模型是否满足空间服务要求并可用于保存定义。")
        boolean selectable,
        @Schema(description = "selectable=false 时的具体原因，例如模型未发布、来源不可用、Geometry 字段不唯一、缺少 EPSG 或缺少唯一主键；可选择时为空。")
        String unavailableReason,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
}
