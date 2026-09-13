package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Immutable table and field snapshot used by an Engine deployment. */
public record StandardServiceDefinition(
        @JsonPropertyDescription("Canvas 或执行协议版本。")
        int protocolVersion,
        @JsonPropertyDescription("物理表 Catalog；数据库不支持或未使用时为空。")
        String catalogName,
        @JsonPropertyDescription("默认数据库 Schema；产品不支持或未指定时为空。")
        String schemaName,
        @JsonPropertyDescription("数据服务读取的数据库物理表名。")
        @NotBlank String physicalTableName,
        @JsonPropertyDescription("部署时固定的可查询字段定义，按模型字段顺序排列；查询、过滤、排序、分组和聚合只能引用其中允许相应操作的字段编码。")
        @NotEmpty List<@Valid ServiceFieldDefinition> fields
) {

    public StandardServiceDefinition {
        if (protocolVersion != 1) {
            throw new IllegalArgumentException("Unsupported standard service protocol version: " + protocolVersion);
        }
        fields = List.copyOf(fields);
    }
}
