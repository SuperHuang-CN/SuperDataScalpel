package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionChoice;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionDefinition;
import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionType;
import cn.superhuang.data.scalpel.dialect.api.NamespaceMode;
import cn.superhuang.data.scalpel.dialect.api.JdbcIncrementalReadDialect;
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
import java.util.Properties;
import java.util.Set;

public final class KingbaseDialect extends AbstractJdbcDialect implements JdbcIncrementalReadDialect {

    public KingbaseDialect() {
        super(
                "KINGBASE", "人大金仓", 54321,
                "数据库", "Schema", "public", NamespaceMode.SCHEMA,
                List.of(new ConnectionOptionDefinition(
                        "sslmode", "SSL 模式", ConnectionOptionType.SELECT, null,
                        List.of(
                                new ConnectionOptionChoice("disable", "禁用"),
                                new ConnectionOptionChoice("prefer", "优先"),
                                new ConnectionOptionChoice("require", "必须")
                        )
                )),
                "com.kingbase8.Driver", "\"", "\"", QualificationMode.SCHEMA, PreviewStyle.LIMIT
        );
    }

    @Override
    public JdbcConnectionSpec createConnectionSpec(JdbcConnectionConfig config) {
        Properties properties = baseProperties(config);
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "15");
        applyConnectionOptions(
                config, properties, Set.of(),
                Set.of("connectTimeout", "socketTimeout", "currentSchema")
        );
        String url = "jdbc:kingbase8://" + hostForUrl(config) + ":" + config.port() + "/" + pathSegment(config.databaseName());
        return new JdbcConnectionSpec(driverClassName(), url, properties, config.schemaName());
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
                WITH RECURSIVE target AS (
                    SELECT c.oid, c.relkind
                    FROM sys_catalog.sys_class c
                    JOIN sys_catalog.sys_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = ? AND c.relname = ?
                ), relations AS (
                    SELECT oid, relkind FROM target
                    UNION ALL
                    SELECT child.oid, child.relkind
                    FROM relations parent
                    JOIN sys_catalog.sys_inherits inheritance ON inheritance.inhparent = parent.oid
                    JOIN sys_catalog.sys_class child ON child.oid = inheritance.inhrelid
                )
                SELECT
                    (SELECT CAST(relkind AS varchar) FROM target),
                    CASE WHEN SUM(CASE WHEN reltuples >= 0 THEN 1 ELSE 0 END) = 0 THEN NULL
                         ELSE CAST(SUM(CASE WHEN reltuples >= 0 THEN reltuples ELSE 0 END) AS bigint) END,
                    CAST(SUM(CASE WHEN relkind IN ('r', 'm') THEN sys_total_relation_size(oid) ELSE 0 END) AS bigint)
                FROM relations
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(TableStatisticsJdbcSupport.timeoutSeconds(timeout));
            statement.setString(1, table.schema());
            statement.setString(2, table.table());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getString(1) == null) {
                    return TablePhysicalStatistics.notFound();
                }
                String kind = resultSet.getString(1);
                if ("v".equals(kind)) {
                    return TablePhysicalStatistics.unsupported("普通视图没有独立物理存储统计");
                }
                if (!"r".equals(kind) && !"p".equals(kind) && !"m".equals(kind)) {
                    return TablePhysicalStatistics.unsupported("当前 Kingbase 物理对象类型无法提供表统计");
                }
                return TablePhysicalStatistics.available(
                        TableStatisticsJdbcSupport.nullableLong(resultSet, 2),
                        TableStatisticQuality.ESTIMATED,
                        TableStatisticsJdbcSupport.nullableLong(resultSet, 3),
                        TableStatisticQuality.EXACT
                );
            }
        } catch (SQLException exception) {
            if ("42P01".equals(exception.getSQLState()) || "42883".equals(exception.getSQLState())) {
                return TablePhysicalStatistics.unsupported("当前 Kingbase 版本无法提供物理表快速统计");
            }
            throw exception;
        }
    }
}
