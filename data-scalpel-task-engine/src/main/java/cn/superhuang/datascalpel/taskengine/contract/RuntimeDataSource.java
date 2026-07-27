package cn.superhuang.datascalpel.taskengine.contract;

import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record RuntimeDataSource(
        UUID dataSourceId,
        ConnectionKind connectionKind,
        RuntimeDatabaseType databaseType,
        Set<DataSourcePurpose> purposes,
        RuntimeJdbcConnection connection,
        HttpApiContracts.RuntimeConnection httpApiConnection,
        List<HttpApiContracts.ResourceDefinition> apiResources,
        RuntimeKafkaConnection kafkaConnection
) {
    public RuntimeDataSource {
        purposes = purposes == null ? Set.of() : Set.copyOf(purposes);
        apiResources = apiResources == null ? List.of() : List.copyOf(apiResources);
    }

    public RuntimeDataSource(
            UUID dataSourceId,
            RuntimeDatabaseType databaseType,
            Set<DataSourcePurpose> purposes,
            RuntimeJdbcConnection connection
    ) {
        this(dataSourceId, ConnectionKind.JDBC, databaseType, purposes, connection, null, List.of(), null);
    }

    public RuntimeDataSource(
            UUID dataSourceId,
            ConnectionKind connectionKind,
            RuntimeDatabaseType databaseType,
            Set<DataSourcePurpose> purposes,
            RuntimeJdbcConnection connection,
            HttpApiContracts.RuntimeConnection httpApiConnection,
            List<HttpApiContracts.ResourceDefinition> apiResources
    ) {
        this(dataSourceId, connectionKind, databaseType, purposes, connection, httpApiConnection, apiResources, null);
    }
}
