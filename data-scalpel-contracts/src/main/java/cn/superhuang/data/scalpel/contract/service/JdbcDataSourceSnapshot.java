package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Connection information delivered from the control plane to an Engine.
 * The Engine encrypts the password before persisting this snapshot.
 */
public record JdbcDataSourceSnapshot(
        @NotNull UUID dataSourceId,
        @NotBlank String databaseType,
        @NotBlank String host,
        @Min(1) @Max(65535) int port,
        @NotBlank String databaseName,
        String schemaName,
        @NotBlank String username,
        String password,
        Map<String, String> options
) {

    public JdbcDataSourceSnapshot {
        options = options == null ? Map.of() : Map.copyOf(options);
    }
}
