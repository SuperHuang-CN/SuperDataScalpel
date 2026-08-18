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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

public final class OracleDialect extends AbstractJdbcDialect {

    public OracleDialect() {
        super(
                "ORACLE", "Oracle", 1521,
                "服务名/SID", "Schema", null, NamespaceMode.SCHEMA,
                List.of(new ConnectionOptionDefinition(
                        "connectionMode", "连接方式", ConnectionOptionType.SELECT, "SERVICE",
                        List.of(
                                new ConnectionOptionChoice("SERVICE", "Service Name"),
                                new ConnectionOptionChoice("SID", "SID")
                        )
                )),
                "oracle.jdbc.OracleDriver", "\"", "\"", QualificationMode.SCHEMA, PreviewStyle.FETCH_FIRST
        );
    }

    @Override
    public JdbcConnectionSpec createConnectionSpec(JdbcConnectionConfig config) {
        Properties properties = baseProperties(config);
        properties.setProperty("oracle.net.CONNECT_TIMEOUT", "5000");
        properties.setProperty("oracle.jdbc.ReadTimeout", "15000");
        applyConnectionOptions(
                config, properties, Set.of("connectionMode"),
                Set.of("oracle.net.CONNECT_TIMEOUT", "oracle.jdbc.ReadTimeout")
        );
        String mode = option(config, "connectionMode", "SERVICE").toUpperCase(Locale.ROOT);
        String url = switch (mode) {
            case "SERVICE" -> "jdbc:oracle:thin:@//" + hostForUrl(config) + ":" + config.port() + "/" + pathSegment(config.databaseName());
            case "SID" -> "jdbc:oracle:thin:@" + hostForUrl(config) + ":" + config.port() + ":" + config.databaseName();
            default -> throw new IllegalArgumentException("Oracle 连接方式只支持 SERVICE 或 SID");
        };
        return new JdbcConnectionSpec(driverClassName(), url, properties, config.schemaName());
    }

    @Override
    public String resolveCatalog(JdbcConnectionConfig config, String requestedCatalog) {
        return null;
    }

    @Override
    public String resolveSchema(JdbcConnectionConfig config, String requestedSchema) {
        String resolved = super.resolveSchema(config, requestedSchema);
        return resolved == null ? config.username().toUpperCase(Locale.ROOT) : resolved;
    }

    @Override
    public String validationQuery() {
        return "SELECT 1 FROM DUAL";
    }

    @Override
    public TablePhysicalStatistics readTablePhysicalStatistics(
            Connection connection,
            TableIdentifier table,
            Duration timeout
    ) throws SQLException {
        String owner = table.schema().toUpperCase(Locale.ROOT);
        String tableName = table.table().toUpperCase(Locale.ROOT);
        long deadlineNanos = TableStatisticsJdbcSupport.deadlineNanos(timeout);
        String typeSql = "SELECT OBJECT_TYPE FROM ALL_OBJECTS WHERE OWNER = ? AND OBJECT_NAME = ? "
                + "AND SUBOBJECT_NAME IS NULL AND OBJECT_TYPE IN ('TABLE', 'VIEW', 'MATERIALIZED VIEW')";
        try (PreparedStatement statement = connection.prepareStatement(typeSql)) {
            statement.setQueryTimeout(TableStatisticsJdbcSupport.remainingTimeoutSeconds(deadlineNanos));
            statement.setString(1, owner);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return TablePhysicalStatistics.notFound();
                }
                if ("VIEW".equalsIgnoreCase(resultSet.getString(1))) {
                    return TablePhysicalStatistics.unsupported("普通视图没有独立物理存储统计");
                }
            }
        }

        Long rowCount = queryNullableLong(
                connection,
                "SELECT NUM_ROWS FROM ALL_TABLES WHERE OWNER = ? AND TABLE_NAME = ?",
                owner,
                tableName,
                deadlineNanos
        );
        String sizeSql = """
                SELECT SUM(BYTES)
                FROM ALL_SEGMENTS
                WHERE OWNER = ? AND (
                    (SEGMENT_NAME = ? AND SEGMENT_TYPE LIKE 'TABLE%')
                    OR SEGMENT_NAME IN (SELECT INDEX_NAME FROM ALL_INDEXES WHERE OWNER = ? AND TABLE_NAME = ?)
                    OR SEGMENT_NAME IN (SELECT SEGMENT_NAME FROM ALL_LOBS WHERE OWNER = ? AND TABLE_NAME = ?)
                    OR SEGMENT_NAME IN (SELECT INDEX_NAME FROM ALL_LOBS WHERE OWNER = ? AND TABLE_NAME = ?)
                )
                """;
        Long storageBytes;
        try (PreparedStatement statement = connection.prepareStatement(sizeSql)) {
            statement.setQueryTimeout(TableStatisticsJdbcSupport.remainingTimeoutSeconds(deadlineNanos));
            for (int index = 1; index <= 7; index += 2) {
                statement.setString(index, owner);
                statement.setString(index + 1, tableName);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                storageBytes = resultSet.next() ? TableStatisticsJdbcSupport.nullableLong(resultSet, 1) : null;
            }
        }
        return TablePhysicalStatistics.available(
                rowCount,
                TableStatisticQuality.ESTIMATED,
                storageBytes,
                TableStatisticQuality.EXACT
        );
    }

    private static Long queryNullableLong(
            Connection connection,
            String sql,
            String owner,
            String tableName,
            long deadlineNanos
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(TableStatisticsJdbcSupport.remainingTimeoutSeconds(deadlineNanos));
            statement.setString(1, owner);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? TableStatisticsJdbcSupport.nullableLong(resultSet, 1) : null;
            }
        }
    }
}
