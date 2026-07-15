package cn.superhuang.data.scalpel.engine.query;

import cn.superhuang.data.scalpel.contract.service.JdbcDataSourceSnapshot;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Lazily creates one bounded Hikari pool per runtime data source, shared by all deployed services. */
@Component
public class DataSourcePoolRegistry {

    private final DialectRegistry dialectRegistry;
    private final Map<UUID, PoolHolder> pools = new ConcurrentHashMap<>();

    public DataSourcePoolRegistry(DialectRegistry dialectRegistry) {
        this.dialectRegistry = dialectRegistry;
    }

    public Connection connection(JdbcDataSourceSnapshot snapshot) throws SQLException {
        String fingerprint = fingerprint(snapshot);
        PoolHolder holder = pools.compute(snapshot.dataSourceId(), (ignored, existing) -> {
            if (existing != null && existing.fingerprint().equals(fingerprint)) {
                return existing;
            }
            if (existing != null) {
                existing.dataSource().close();
            }
            return new PoolHolder(fingerprint, create(snapshot));
        });
        Connection connection = holder.dataSource().getConnection();
        try {
            connection.setReadOnly(true);
        } catch (SQLException ignored) {
            // Read-only mode is an optimization and a few JDBC drivers do not support it.
        }
        return connection;
    }

    public void test(JdbcDataSourceSnapshot snapshot) {
        try (HikariDataSource dataSource = create(snapshot); Connection ignored = dataSource.getConnection()) {
            // Obtaining a connection proves both the dialect connection specification and JDBC credentials.
        } catch (SQLException exception) {
            throw new IllegalStateException("无法连接数据源", exception);
        }
    }

    public void evict(UUID dataSourceId) {
        PoolHolder holder = pools.remove(dataSourceId);
        if (holder != null) {
            holder.dataSource().close();
        }
    }

    @PreDestroy
    void closeAll() {
        pools.values().forEach(holder -> holder.dataSource().close());
        pools.clear();
    }

    private HikariDataSource create(JdbcDataSourceSnapshot snapshot) {
        DatabaseDialect dialect = dialectRegistry.require(snapshot.databaseType());
        JdbcConnectionSpec spec = dialect.createConnectionSpec(new JdbcConnectionConfig(
                snapshot.host(), snapshot.port(), snapshot.databaseName(), snapshot.schemaName(), snapshot.username(),
                snapshot.password(), snapshot.options()
        ));
        HikariConfig configuration = new HikariConfig();
        configuration.setPoolName("data-scalpel-" + snapshot.dataSourceId());
        configuration.setJdbcUrl(spec.jdbcUrl());
        configuration.setDriverClassName(spec.driverClassName());
        configuration.setDataSourceProperties(spec.properties());
        configuration.setMinimumIdle(0);
        configuration.setMaximumPoolSize(10);
        configuration.setConnectionTimeout(10_000);
        configuration.setValidationTimeout(5_000);
        configuration.setIdleTimeout(600_000);
        configuration.setMaxLifetime(1_800_000);
        return new HikariDataSource(configuration);
    }

    private static String fingerprint(JdbcDataSourceSnapshot snapshot) {
        return snapshot.databaseType() + "\u0000" + snapshot.host() + "\u0000" + snapshot.port() + "\u0000"
                + snapshot.databaseName() + "\u0000" + snapshot.schemaName() + "\u0000" + snapshot.username()
                + "\u0000" + snapshot.password() + "\u0000" + snapshot.options();
    }

    private record PoolHolder(String fingerprint, HikariDataSource dataSource) {
    }
}
