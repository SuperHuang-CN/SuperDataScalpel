package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDefinition;

import java.util.List;
import java.util.Set;

public record DatabaseTypeResponse(
        String id,
        String displayName,
        int defaultPort,
        String databaseNameLabel,
        String schemaNameLabel,
        String defaultSchema,
        String namespaceMode,
        Set<String> capabilities,
        List<ConnectionOptionResponse> connectionOptions,
        boolean driverAvailable
) {
    public static DatabaseTypeResponse from(DatabaseDefinition definition, boolean driverAvailable) {
        return new DatabaseTypeResponse(
                definition.id(),
                definition.displayName(),
                definition.defaultPort(),
                definition.databaseNameLabel(),
                definition.schemaNameLabel(),
                definition.defaultSchema(),
                definition.namespaceMode().name(),
                definition.capabilities().stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                definition.connectionOptions().stream().map(ConnectionOptionResponse::from).toList(),
                driverAvailable
        );
    }
}
