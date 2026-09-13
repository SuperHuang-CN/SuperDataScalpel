package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceResourceBrowserKind;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDefinition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Set;

/** UI definition of a concrete data-source type and its current runtime availability. */
@Schema(description = "具体数据源类型的界面定义及当前部署可用能力")
public record DataSourceTypeResponse(
        @Schema(description = "数据源类型的稳定枚举值，创建数据源时作为 type 提交") String id,
        @Schema(description = "数据源类型中文名称") String displayName,
        @Schema(description = "连接 DTO 种类，决定 connection 字段结构") String connectionKind,
        @Schema(description = "该类型允许承担的业务用途") Set<String> supportedPurposes,
        @Schema(description = "当前部署是否支持连接测试；会受驱动和实现能力影响") boolean connectionTestAvailable,
        @Schema(description = "当前部署是否支持浏览元数据；会受驱动和实现能力影响") boolean metadataAvailable,
        @Schema(description = "前端应使用的资源浏览器类型") DataSourceResourceBrowserKind resourceBrowserKind,
        @Schema(description = "JDBC 产品建议的默认服务端口；HTTP API、Kafka、S3 等类型为空") Integer defaultPort,
        @Schema(description = "该 JDBC 产品对 databaseName 输入项使用的界面名称，例如数据库或实例；非 JDBC 类型为空") String databaseNameLabel,
        @Schema(description = "该 JDBC 产品对 schemaName 输入项使用的界面名称；不使用 Schema 的产品和非 JDBC 类型为空") String schemaNameLabel,
        @Schema(description = "未显式配置 schemaName 时建议使用的 Schema；产品没有默认 Schema 或非 JDBC 类型时为空") String defaultSchema,
        @Schema(description = "该数据库对 Catalog 与 Schema 的命名空间使用方式") String namespaceMode,
        @Schema(description = "方言公开能力，例如 JDBC_INCREMENTAL_READ、TMQ_SUBSCRIBE") Set<String> capabilities,
        @Schema(description = "当前方言允许配置的高级连接参数") List<ConnectionOptionResponse> connectionOptions,
        @Schema(description = "当前部署是否存在该类型所需 JDBC 驱动；非 JDBC 类型按实现能力返回") boolean driverAvailable
) {
    public static DataSourceTypeResponse jdbc(DatabaseDefinition definition, boolean driverAvailable) {
        DataSourceType type = DataSourceType.valueOf(definition.id());
        return new DataSourceTypeResponse(
                definition.id(), definition.displayName(), DataSourceConnectionKind.JDBC.name(), purposes(type),
                driverAvailable, driverAvailable,
                type.isTdEngine()
                        ? DataSourceResourceBrowserKind.TDENGINE_SUPERTABLES
                        : DataSourceResourceBrowserKind.JDBC_TABLES,
                definition.defaultPort(), definition.databaseNameLabel(),
                definition.schemaNameLabel(), definition.defaultSchema(), definition.namespaceMode().name(),
                definition.capabilities().stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                definition.connectionOptions().stream().map(ConnectionOptionResponse::from).toList(), driverAvailable
        );
    }

    public static DataSourceTypeResponse kafka() {
        return nonJdbc(DataSourceType.KAFKA, DataSourceResourceBrowserKind.KAFKA_TOPICS, true);
    }

    public static DataSourceTypeResponse s3() {
        return nonJdbc(DataSourceType.S3, DataSourceResourceBrowserKind.NONE, true);
    }

    public static DataSourceTypeResponse httpApi() {
        DataSourceType type = DataSourceType.HTTP_API;
        return new DataSourceTypeResponse(
                type.name(), type.displayName(), type.connectionKind().name(), purposes(type),
                true, false, DataSourceResourceBrowserKind.API_RESOURCES,
                null, null, null, null, null, Set.of(), List.of(), true
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
                true, true, DataSourceResourceBrowserKind.SPATIAL_RESOURCES,
                null, null, null, null, null, Set.of(), List.of(), true
        );
    }

    private static DataSourceTypeResponse nonJdbc(
            DataSourceType type,
            DataSourceResourceBrowserKind resourceBrowserKind,
            boolean runtimeAvailable
    ) {
        return new DataSourceTypeResponse(
                type.name(), type.displayName(), type.connectionKind().name(), purposes(type),
                runtimeAvailable, false, resourceBrowserKind, null, null, null, null, null,
                Set.of(), List.of(), runtimeAvailable
        );
    }

    private static Set<String> purposes(DataSourceType type) {
        return type.supportedPurposes().stream().map(DataSourcePurpose::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
