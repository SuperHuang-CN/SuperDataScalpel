package cn.superhuang.data.scalpel.dialect.api;

import java.util.List;
import java.util.Set;

public record DatabaseDefinition(
        String id,
        String displayName,
        int defaultPort,
        String databaseNameLabel,
        String schemaNameLabel,
        String defaultSchema,
        NamespaceMode namespaceMode,
        Set<DatabaseCapability> capabilities,
        List<ConnectionOptionDefinition> connectionOptions
) {
    public DatabaseDefinition {
        capabilities = Set.copyOf(capabilities);
        connectionOptions = List.copyOf(connectionOptions);
    }
}
