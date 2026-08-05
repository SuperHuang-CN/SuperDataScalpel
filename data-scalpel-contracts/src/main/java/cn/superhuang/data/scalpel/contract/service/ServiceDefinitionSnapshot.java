package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Explicit one-of deployment definition persisted by a Service Engine. */
public record ServiceDefinitionSnapshot(
        @NotNull DataServiceType type,
        @Valid StandardServiceDefinition standardDefinition,
        @Valid SqlServiceDefinition sqlDefinition,
        @Valid ScriptServiceDefinition scriptDefinition
) {

    public ServiceDefinitionSnapshot(
            DataServiceType type,
            StandardServiceDefinition standardDefinition,
            SqlServiceDefinition sqlDefinition
    ) {
        this(type, standardDefinition, sqlDefinition, null);
    }

    public ServiceDefinitionSnapshot {
        if (type == null) {
            throw new IllegalArgumentException("Service type is required");
        }
        boolean standard = standardDefinition != null;
        boolean sql = sqlDefinition != null;
        boolean script = scriptDefinition != null;
        if ((standard ? 1 : 0) + (sql ? 1 : 0) + (script ? 1 : 0) != 1) {
            throw new IllegalArgumentException("Exactly one service definition is required");
        }
        if (type == DataServiceType.STANDARD_TABLE && !standard) {
            throw new IllegalArgumentException("STANDARD_TABLE requires a standard definition");
        }
        if (type == DataServiceType.SQL_QUERY && !sql) {
            throw new IllegalArgumentException("SQL_QUERY requires a SQL definition");
        }
        if (type == DataServiceType.SCRIPT_API && !script) {
            throw new IllegalArgumentException("SCRIPT_API requires a script definition");
        }
    }

    public static ServiceDefinitionSnapshot standard(StandardServiceDefinition definition) {
        return new ServiceDefinitionSnapshot(DataServiceType.STANDARD_TABLE, definition, null, null);
    }

    public static ServiceDefinitionSnapshot sql(SqlServiceDefinition definition) {
        return new ServiceDefinitionSnapshot(DataServiceType.SQL_QUERY, null, definition, null);
    }

    public static ServiceDefinitionSnapshot script(ScriptServiceDefinition definition) {
        return new ServiceDefinitionSnapshot(DataServiceType.SCRIPT_API, null, null, definition);
    }
}
