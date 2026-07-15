package cn.superhuang.data.scalpel.contract.service;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** A published model field and its physical column. */
public record ServiceFieldDefinition(
        @NotBlank String code,
        @NotBlank String physicalColumn,
        @NotNull PlatformDataType type,
        boolean nullable,
        boolean primaryKey
) {
}
