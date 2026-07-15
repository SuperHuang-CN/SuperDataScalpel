package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Immutable table and field snapshot used by an Engine deployment. */
public record StandardServiceDefinition(
        int protocolVersion,
        String catalogName,
        String schemaName,
        @NotBlank String physicalTableName,
        @NotEmpty List<@Valid ServiceFieldDefinition> fields
) {

    public StandardServiceDefinition {
        if (protocolVersion != 1) {
            throw new IllegalArgumentException("Unsupported standard service protocol version: " + protocolVersion);
        }
        fields = List.copyOf(fields);
    }
}
