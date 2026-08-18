package cn.superhuang.datascalpel.taskengine.contract;

import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import cn.superhuang.data.scalpel.contract.task.SpatialServiceResourceDefinition;

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
        RuntimeKafkaConnection kafkaConnection,
        RuntimeS3Connection s3Connection,
        List<SpatialServiceResourceDefinition> spatialResources,
        RuntimeTdEngineTmqConnection tdEngineTmqConnection
) {
    public RuntimeDataSource {
        purposes = purposes == null ? Set.of() : Set.copyOf(purposes);
        apiResources = apiResources == null ? List.of() : List.copyOf(apiResources);
        spatialResources = spatialResources == null ? List.of() : List.copyOf(spatialResources);
    }

    public RuntimeDataSource(
            UUID dataSourceId,
            ConnectionKind connectionKind,
            RuntimeDatabaseType databaseType,
            Set<DataSourcePurpose> purposes,
            RuntimeJdbcConnection connection,
            HttpApiContracts.RuntimeConnection httpApiConnection,
            List<HttpApiContracts.ResourceDefinition> apiResources,
            RuntimeKafkaConnection kafkaConnection,
            RuntimeS3Connection s3Connection,
            List<SpatialServiceResourceDefinition> spatialResources
    ) {
        this(dataSourceId, connectionKind, databaseType, purposes, connection,
                httpApiConnection, apiResources, kafkaConnection, s3Connection,
                spatialResources, null);
    }

    public RuntimeDataSource(
            UUID dataSourceId,
            RuntimeDatabaseType databaseType,
            Set<DataSourcePurpose> purposes,
            RuntimeJdbcConnection connection
    ) {
        this(dataSourceId, ConnectionKind.JDBC, databaseType, purposes, connection,
                null, List.of(), null, null, List.of(), null);
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
        this(dataSourceId, connectionKind, databaseType, purposes, connection,
                httpApiConnection, apiResources, null, null, List.of(), null);
    }

    public RuntimeDataSource(
            UUID dataSourceId,
            ConnectionKind connectionKind,
            RuntimeDatabaseType databaseType,
            Set<DataSourcePurpose> purposes,
            RuntimeJdbcConnection connection,
            HttpApiContracts.RuntimeConnection httpApiConnection,
            List<HttpApiContracts.ResourceDefinition> apiResources,
            RuntimeKafkaConnection kafkaConnection
    ) {
        this(dataSourceId, connectionKind, databaseType, purposes, connection,
                httpApiConnection, apiResources, kafkaConnection, null, List.of(), null);
    }

    public RuntimeDataSource(
            UUID dataSourceId,
            ConnectionKind connectionKind,
            RuntimeDatabaseType databaseType,
            Set<DataSourcePurpose> purposes,
            RuntimeJdbcConnection connection,
            HttpApiContracts.RuntimeConnection httpApiConnection,
            List<HttpApiContracts.ResourceDefinition> apiResources,
            RuntimeKafkaConnection kafkaConnection,
            RuntimeS3Connection s3Connection
    ) {
        this(dataSourceId, connectionKind, databaseType, purposes, connection,
                httpApiConnection, apiResources, kafkaConnection, s3Connection, List.of(), null);
    }
}
