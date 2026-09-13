package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** One named scalar argument accepted by a published SQL service. */
@JsonClassDescription("SQL 数据服务模板中的一个命名输入参数定义；规定平台类型、是否必填及业务说明。")
public record SqlServiceParameterDefinition(
        @JsonPropertyDescription("SQL 请求参数名，必须与 SQL 中引用的命名参数一致，并匹配字母开头的英文标识符规则。")
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}") String name,
        @JsonPropertyDescription("字段的平台类型及长度、精度、标度或 Geometry 参数。")
        @NotNull @Valid PlatformTypeDefinition typeDefinition,
        @JsonPropertyDescription("调用服务时是否必须提供非空值；false 时省略或显式传 null 都按 SQL NULL 绑定。")
        boolean required,
        @JsonPropertyDescription("参数的业务含义、取值口径或单位；未填写时为空。")
        @Size(max = 500) String description
) {

    public SqlServiceParameterDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("SQL parameter name is required");
        }
        name = name.trim();
        if (!name.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Invalid SQL parameter name: " + name);
        }
        if (typeDefinition == null) {
            throw new IllegalArgumentException("SQL parameter type is required");
        }
        if (typeDefinition.type() == PlatformDataType.BINARY) {
            throw new IllegalArgumentException("BINARY SQL parameters are not supported");
        }
        if (typeDefinition.type() == PlatformDataType.GEOMETRY) {
            throw new IllegalArgumentException("GEOMETRY SQL parameters are not supported");
        }
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
