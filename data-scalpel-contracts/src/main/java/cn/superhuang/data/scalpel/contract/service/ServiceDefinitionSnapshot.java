package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Explicit one-of deployment definition persisted by a Service Engine. */
public record ServiceDefinitionSnapshot(
        @JsonPropertyDescription("服务类型；必须且只能提供与 STANDARD_TABLE、SQL_QUERY、SCRIPT_API 或 SPATIAL_SERVICE 对应的一种定义。")
        @NotNull DataServiceType type,
        @JsonPropertyDescription("STANDARD 类型服务的结构化单表查询定义；其他类型为空。")
        @Valid StandardServiceDefinition standardDefinition,
        @JsonPropertyDescription("SQL 类型服务的参数化只读 SQL 定义；其他类型为空。")
        @Valid SqlServiceDefinition sqlDefinition,
        @JsonPropertyDescription("SCRIPT 类型服务的受限脚本定义；其他类型为空。")
        @Valid ScriptServiceDefinition scriptDefinition,
        @JsonPropertyDescription("SPATIAL 类型服务的物理表、Geometry 和发布图层定义；其他类型为空。")
        @Valid SpatialServiceDefinition spatialDefinition
) {

    public ServiceDefinitionSnapshot(
            DataServiceType type,
            StandardServiceDefinition standardDefinition,
            SqlServiceDefinition sqlDefinition
    ) {
        this(type, standardDefinition, sqlDefinition, null, null);
    }

    public ServiceDefinitionSnapshot {
        if (type == null) {
            throw new IllegalArgumentException("Service type is required");
        }
        boolean standard = standardDefinition != null;
        boolean sql = sqlDefinition != null;
        boolean script = scriptDefinition != null;
        boolean spatial = spatialDefinition != null;
        if ((standard ? 1 : 0) + (sql ? 1 : 0) + (script ? 1 : 0) + (spatial ? 1 : 0) != 1) {
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
        if (type == DataServiceType.SPATIAL_SERVICE && !spatial) {
            throw new IllegalArgumentException("SPATIAL_SERVICE requires a spatial definition");
        }
    }

    public static ServiceDefinitionSnapshot standard(StandardServiceDefinition definition) {
        return new ServiceDefinitionSnapshot(DataServiceType.STANDARD_TABLE, definition, null, null, null);
    }

    public static ServiceDefinitionSnapshot sql(SqlServiceDefinition definition) {
        return new ServiceDefinitionSnapshot(DataServiceType.SQL_QUERY, null, definition, null, null);
    }

    public static ServiceDefinitionSnapshot script(ScriptServiceDefinition definition) {
        return new ServiceDefinitionSnapshot(DataServiceType.SCRIPT_API, null, null, definition, null);
    }

    public static ServiceDefinitionSnapshot spatial(SpatialServiceDefinition definition) {
        return new ServiceDefinitionSnapshot(DataServiceType.SPATIAL_SERVICE, null, null, null, definition);
    }
}
