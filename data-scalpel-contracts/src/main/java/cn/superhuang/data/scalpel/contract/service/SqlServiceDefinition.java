package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Immutable compiled SQL and output contract deployed to a Service Engine. */
public record SqlServiceDefinition(
        @JsonPropertyDescription("Canvas 或执行协议版本。")
        int protocolVersion,
        @JsonPropertyDescription("已通过单条只读查询校验的参数化 JDBC SQL。")
        @NotBlank @Size(max = 100_000) String jdbcSql,
        @JsonPropertyDescription("SQL 占位符对应的参数编码顺序。")
        @Size(max = 200) List<@NotBlank String> bindingOrder,
        @JsonPropertyDescription("服务允许接收的命名参数定义；名称在列表内唯一且覆盖 bindingOrder 中的全部参数。")
        @Size(max = 50) List<@Valid SqlServiceParameterDefinition> parameters,
        @JsonPropertyDescription("通过 JDBC 元数据检查确认的结果字段定义列表。")
        @NotEmpty @Size(max = 200) List<@Valid SqlServiceResultFieldDefinition> resultFields
) {

    public SqlServiceDefinition {
        if (protocolVersion != 1) {
            throw new IllegalArgumentException("Unsupported SQL service protocol version: " + protocolVersion);
        }
        if (jdbcSql == null || jdbcSql.isBlank()) {
            throw new IllegalArgumentException("Compiled SQL is required");
        }
        jdbcSql = jdbcSql.trim();
        bindingOrder = bindingOrder == null ? List.of() : List.copyOf(bindingOrder);
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        resultFields = resultFields == null ? List.of() : List.copyOf(resultFields);
        if (bindingOrder.size() > 200 || parameters.size() > 50 || resultFields.isEmpty() || resultFields.size() > 200) {
            throw new IllegalArgumentException("SQL service definition exceeds protocol limits");
        }
        Set<String> parameterNames = new HashSet<>();
        for (SqlServiceParameterDefinition parameter : parameters) {
            if (!parameterNames.add(parameter.name())) {
                throw new IllegalArgumentException("Duplicate SQL parameter: " + parameter.name());
            }
        }
        if (!parameterNames.containsAll(bindingOrder)) {
            throw new IllegalArgumentException("SQL binding order contains an undeclared parameter");
        }
        Set<String> outputNames = new HashSet<>();
        for (SqlServiceResultFieldDefinition field : resultFields) {
            if (!outputNames.add(field.name().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate SQL result field: " + field.name());
            }
        }
    }
}
