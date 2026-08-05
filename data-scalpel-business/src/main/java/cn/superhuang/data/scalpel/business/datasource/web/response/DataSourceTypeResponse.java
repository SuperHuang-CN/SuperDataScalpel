package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDefinition;

import java.util.List;
import java.util.Set;

/** UI definition of a concrete data-source type and its current runtime availability. */
public record DataSourceTypeResponse(
        String id,
        String displayName,
        String connectionKind,
        Set<String> supportedPurposes,
        boolean connectionTestAvailable,
        boolean metadataAvailable,
        Integer defaultPort,
        String databaseNameLabel,
        String schemaNameLabel,
        String defaultSchema,
        String namespaceMode,
        Set<String> capabilities,
        List<ConnectionOptionResponse> connectionOptions,
        boolean driverAvailable
) {
    public static DataSourceTypeResponse jdbc(DatabaseDefinition definition, boolean driverAvailable) {
        DataSourceType type = DataSourceType.valueOf(definition.id());
        return new DataSourceTypeResponse(
                definition.id(), definition.displayName(), DataSourceConnectionKind.JDBC.name(), purposes(type),
                driverAvailable, driverAvailable, definition.defaultPort(), definition.databaseNameLabel(),
                definition.schemaNameLabel(), definition.defaultSchema(), definition.namespaceMode().name(),
                definition.capabilities().stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                definition.connectionOptions().stream().map(ConnectionOptionResponse::from).toList(), driverAvailable
        );
    }

    public static DataSourceTypeResponse kafka() {
        return nonJdbc(DataSourceType.KAFKA);
    }

    public static DataSourceTypeResponse s3() {
        return nonJdbc(DataSourceType.S3);
    }

    public static DataSourceTypeResponse httpApi() {
        DataSourceType type = DataSourceType.HTTP_API;
        return new DataSourceTypeResponse(
                type.name(), type.displayName(), type.connectionKind().name(), purposes(type),
                true, false, null, null, null, null, null, Set.of(), List.of(), true
        );
    }

    public static DataSourceTypeResponse arcgisRest() {
        return httpBacked(DataSourceType.ARCGIS_REST);
    }

    public static DataSourceTypeResponse wfs() {
        return httpBacked(DataSourceType.WFS);
    }

    private static DataSourceTypeResponse httpBacked(DataSourceType type) {
        return new DataSourceTypeResponse(
                type.name(), type.displayName(), type.connectionKind().name(), purposes(type),
                true, true, null, null, null, null, null, Set.of(), List.of(), true
        );
    }

    private static DataSourceTypeResponse nonJdbc(DataSourceType type) {
        return new DataSourceTypeResponse(
                type.name(), type.displayName(), type.connectionKind().name(), purposes(type),
                false, false, null, null, null, null, null, Set.of(), List.of(), false
        );
    }

    private static Set<String> purposes(DataSourceType type) {
        return type.supportedPurposes().stream().map(DataSourcePurpose::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
