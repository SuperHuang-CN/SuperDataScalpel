package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionChoice;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionDefinition;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionType;
import cn.superhuang.data.scalpel.dialect.api.NamespaceMode;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TablePhysicalStatistics;
import cn.superhuang.data.scalpel.dialect.model.TableStatisticQuality;
import cn.superhuang.data.scalpel.dialect.query.InsertSelectQuery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class SqlServerDialect extends AbstractJdbcDialect {

    public SqlServerDialect() {
        super(
                "SQL_SERVER", "SQL Server", 1433,
                "数据库", "Schema", "dbo", NamespaceMode.CATALOG_AND_SCHEMA,
                List.of(
                        booleanOption("encrypt", "加密连接", "true"),
                        booleanOption("trustServerCertificate", "信任服务器证书", "false")
                ),
                "com.microsoft.sqlserver.jdbc.SQLServerDriver", "[", "]", QualificationMode.CATALOG_AND_SCHEMA, PreviewStyle.TOP
        );
    }

    @Override
    public JdbcConnectionSpec createConnectionSpec(JdbcConnectionConfig config) {
        Properties properties = baseProperties(config);
        properties.setProperty("databaseName", config.databaseName());
        properties.setProperty("loginTimeout", "5");
        properties.setProperty("socketTimeout", "15000");
        properties.setProperty("encrypt", option(config, "encrypt", "true"));
        properties.setProperty("trustServerCertificate", option(config, "trustServerCertificate", "false"));
        applyConnectionOptions(
                config, properties, Set.of(),
                Set.of("databaseName", "loginTimeout", "socketTimeout")
        );
        String url = "jdbc:sqlserver://" + hostForUrl(config) + ":" + config.port();
        // The Microsoft JDBC driver does not support changing the default schema for a session.
        return new JdbcConnectionSpec(driverClassName(), url, properties, null);
    }

    @Override
    public String resolveCatalog(JdbcConnectionConfig config, String requestedCatalog) {
        return optional(requestedCatalog) == null ? config.databaseName() : requestedCatalog.trim();
    }

    @Override
    public TablePhysicalStatistics readTablePhysicalStatistics(
            Connection connection,
            TableIdentifier table,
            Duration timeout
    ) throws SQLException {
        String sql = """
                SELECT object.type,
                       SUM(CASE WHEN stats.index_id IN (0, 1) THEN stats.row_count ELSE 0 END),
                       SUM(stats.reserved_page_count) * CAST(8192 AS bigint)
                FROM sys.objects object
                JOIN sys.schemas schema_info ON schema_info.schema_id = object.schema_id
                LEFT JOIN sys.dm_db_partition_stats stats ON stats.object_id = object.object_id
                WHERE schema_info.name = ? AND object.name = ? AND object.type IN ('U', 'V')
                GROUP BY object.type
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(TableStatisticsJdbcSupport.timeoutSeconds(timeout));
            statement.setString(1, table.schema());
            statement.setString(2, table.table());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return TablePhysicalStatistics.notFound();
                }
                if ("V".equalsIgnoreCase(resultSet.getString(1))) {
                    return TablePhysicalStatistics.unsupported("普通视图没有独立物理存储统计");
                }
                return TablePhysicalStatistics.available(
                        TableStatisticsJdbcSupport.nullableLong(resultSet, 2),
                        TableStatisticQuality.ESTIMATED,
                        TableStatisticsJdbcSupport.nullableLong(resultSet, 3),
                        TableStatisticQuality.EXACT
                );
            }
        }
    }

    @Override
    public String renderInsertSelect(TableIdentifier target, List<String> targetColumns, InsertSelectQuery query) {
        String rendered = super.renderInsertSelect(target, targetColumns, query);
        if (query.withClause() == null) {
            return rendered;
        }
        String insert = "INSERT INTO " + qualifiedName(target) + " (" + targetColumns.stream()
                .map(column -> quoteIdentifier(column.trim()))
                .collect(java.util.stream.Collectors.joining(", ")) + ") ";
        return query.withClause() + " " + insert + query.selectSql();
    }

    private static ConnectionOptionDefinition booleanOption(String key, String label, String defaultValue) {
        return new ConnectionOptionDefinition(
                key, label, ConnectionOptionType.BOOLEAN, defaultValue,
                List.of(new ConnectionOptionChoice("true", "是"), new ConnectionOptionChoice("false", "否"))
        );
    }
}
