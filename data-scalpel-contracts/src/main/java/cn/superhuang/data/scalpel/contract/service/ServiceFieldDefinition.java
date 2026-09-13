package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** A published model field and its physical column. */
public record ServiceFieldDefinition(
        @JsonPropertyDescription("已发布服务字段编码，查询、过滤、排序和响应对象均通过该值引用字段。")
        @NotBlank String code,
        @JsonPropertyDescription("结果字段对应的数据库物理列名。")
        @NotBlank String physicalColumn,
        @JsonPropertyDescription("字段的平台逻辑数据类型，用于稳定解释服务输入输出值。")
        @NotNull PlatformDataType type,
        @JsonPropertyDescription("结果是否允许为空。")
        boolean nullable,
        @JsonPropertyDescription("是否为主键字段。")
        boolean primaryKey
) {
}
