package cn.superhuang.data.scalpel.contract.service;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** One named scalar argument accepted by a published SQL service. */
public record SqlServiceParameterDefinition(
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}") String name,
        @NotNull @Valid PlatformTypeDefinition typeDefinition,
        boolean required,
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
