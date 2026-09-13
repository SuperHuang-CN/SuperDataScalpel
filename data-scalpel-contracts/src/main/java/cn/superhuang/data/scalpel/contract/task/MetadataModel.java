package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.UUID;

@JsonClassDescription("供 Canvas 编译使用的纳管模型元数据快照；固定模型版本、物理位置、字段 Schema 与候选唯一键。")
public record MetadataModel(
        @JsonPropertyDescription("纳管模型 UUID。")
        UUID id,
        @JsonPropertyDescription("模型稳定技术编码。")
        String code,
        @JsonPropertyDescription("模型显示名称。")
        String name,
        @JsonPropertyDescription("模型 Schema 版本。")
        int schemaVersion,
        @JsonPropertyDescription("模型生命周期：DRAFT 草稿，PUBLISHED 已发布，DISABLED 已停用。")
        MetadataModelStatus status,
        @JsonPropertyDescription("物理表管理模式：MANAGED 由平台创建和维护，EXTERNAL 引用外部已有表。")
        MetadataModelPhysicalTableMode physicalTableMode,
        @JsonPropertyDescription("数据源 UUID。")
        UUID dataSourceId,
        @JsonPropertyDescription("数据库 Catalog；不适用时为空。")
        String catalogName,
        @JsonPropertyDescription("数据库 Schema；不适用时为空。")
        String schemaName,
        @JsonPropertyDescription("数据库物理表名。")
        String physicalTableName,
        @JsonPropertyDescription("当前 Schema 版本对应的有序物理字段定义。")
        List<CanvasColumnSchema> columns,
        @JsonPropertyDescription("可用于唯一定位记录的候选键列表。")
        List<MetadataUniqueKey> uniqueKeys,
        @JsonPropertyDescription("当前模型 Schema 中带字段 UUID、稳定编码和排序值的字段身份列表；用于把 Canvas 列解析到纳管模型字段。")
        List<MetadataModelField> fields
) {
    public MetadataModel {
        columns = columns == null ? List.of() : List.copyOf(columns);
        uniqueKeys = uniqueKeys == null ? List.of() : List.copyOf(uniqueKeys);
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public MetadataModel(
            UUID id,
            String code,
            String name,
            int schemaVersion,
            MetadataModelStatus status,
            MetadataModelPhysicalTableMode physicalTableMode,
            UUID dataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            List<CanvasColumnSchema> columns
    ) {
        this(id, code, name, schemaVersion, status, physicalTableMode, dataSourceId,
                catalogName, schemaName, physicalTableName, columns, List.of(), List.of());
    }

    public MetadataModel(
            UUID id,
            String code,
            String name,
            int schemaVersion,
            MetadataModelStatus status,
            MetadataModelPhysicalTableMode physicalTableMode,
            UUID dataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            List<CanvasColumnSchema> columns,
            List<MetadataUniqueKey> uniqueKeys
    ) {
        this(id, code, name, schemaVersion, status, physicalTableMode, dataSourceId,
                catalogName, schemaName, physicalTableName, columns, uniqueKeys, List.of());
    }
}
