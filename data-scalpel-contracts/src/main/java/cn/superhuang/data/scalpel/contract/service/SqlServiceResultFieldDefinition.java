package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Immutable output column discovered when a SQL service is published. */
@JsonClassDescription("SQL 数据服务公开结果中的一个有序字段定义；固定字段名、平台逻辑类型和可空性。")
public record SqlServiceResultFieldDefinition(
        @JsonPropertyDescription("SQL 查询结果列名，在当前服务结果 Schema 中唯一。")
        @NotBlank String name,
        @JsonPropertyDescription("字段的平台类型及长度、精度、标度或 Geometry 参数。")
        @NotNull @Valid PlatformTypeDefinition typeDefinition,
        @JsonPropertyDescription("结果是否允许为空。")
        boolean nullable
) {

    public SqlServiceResultFieldDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("SQL result field name is required");
        }
        name = name.trim();
        if (typeDefinition == null) {
            throw new IllegalArgumentException("SQL result field type is required");
        }
        if (typeDefinition.type() == PlatformDataType.BINARY) {
            throw new IllegalArgumentException("BINARY SQL result fields are not supported");
        }
    }
}
