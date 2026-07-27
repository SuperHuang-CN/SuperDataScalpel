package cn.superhuang.data.scalpel.contract.service;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Immutable output column discovered when a SQL service is published. */
public record SqlServiceResultFieldDefinition(
        @NotBlank String name,
        @NotNull @Valid PlatformTypeDefinition typeDefinition,
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
