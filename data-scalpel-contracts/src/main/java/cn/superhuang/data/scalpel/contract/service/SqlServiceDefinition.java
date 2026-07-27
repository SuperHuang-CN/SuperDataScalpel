package cn.superhuang.data.scalpel.contract.service;

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
        int protocolVersion,
        @NotBlank @Size(max = 100_000) String jdbcSql,
        @Size(max = 200) List<@NotBlank String> bindingOrder,
        @Size(max = 50) List<@Valid SqlServiceParameterDefinition> parameters,
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
