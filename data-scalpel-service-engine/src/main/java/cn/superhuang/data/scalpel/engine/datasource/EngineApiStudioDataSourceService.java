package cn.superhuang.data.scalpel.engine.datasource;

import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationResponse;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRemovalRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceStatus;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceTestResponse;
import cn.superhuang.data.scalpel.contract.service.JdbcDataSourceSnapshot;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.engine.config.EngineProperties;
import cn.superhuang.data.scalpel.engine.deployment.EngineDeploymentRecordStatus;
import cn.superhuang.data.scalpel.engine.deployment.EngineDeploymentRepository;
import cn.superhuang.superops.api.studio.datasource.ApiDataSourceRegistry;
import cn.superhuang.superops.api.studio.datasource.DataSourceDialect;
import cn.superhuang.superops.api.studio.datasource.JdbcDataSourceAccess;
import cn.superhuang.superops.api.studio.datasource.application.DataSourceService;
import cn.superhuang.superops.api.studio.datasource.factory.ClickHouseDriver;
import cn.superhuang.superops.api.studio.datasource.factory.DmDriver;
import cn.superhuang.superops.api.studio.datasource.factory.KingBaseDriver;
import cn.superhuang.superops.api.studio.datasource.factory.MySQLDriver;
import cn.superhuang.superops.api.studio.datasource.factory.OpenGaussDriver;
import cn.superhuang.superops.api.studio.datasource.factory.OracleDriver;
import cn.superhuang.superops.api.studio.datasource.factory.PostgreSQLDriver;
import cn.superhuang.superops.api.studio.datasource.factory.HighGoDriver;
import cn.superhuang.superops.api.studio.datasource.factory.SQLServerDriver;
import cn.superhuang.superops.api.studio.entity.DBConfig;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Adapts Engine data source control requests to API Studio's persisted data source management. */
@Service
public class EngineApiStudioDataSourceService {

    private static final Map<String, String> DRIVER_BY_DATABASE_TYPE = Map.ofEntries(
            Map.entry("POSTGRESQL", PostgreSQLDriver.class.getName()),
            Map.entry("HIGHGO", HighGoDriver.class.getName()),
            Map.entry("MYSQL", MySQLDriver.class.getName()),
            Map.entry("ORACLE", OracleDriver.class.getName()),
            Map.entry("SQL_SERVER", SQLServerDriver.class.getName()),
            Map.entry("CLICKHOUSE", ClickHouseDriver.class.getName()),
            Map.entry("DAMENG", DmDriver.class.getName()),
            Map.entry("KINGBASE", KingBaseDriver.class.getName()),
            Map.entry("OPENGAUSS", OpenGaussDriver.class.getName())
    );
    private static final Map<String, String> DATABASE_TYPE_BY_DRIVER = DRIVER_BY_DATABASE_TYPE.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

    private final DataSourceService dataSourceService;
    private final ApiDataSourceRegistry apiDataSourceRegistry;
    private final DialectRegistry dialectRegistry;
    private final EngineDeploymentRepository deploymentRepository;
    private final EngineProperties properties;

    public EngineApiStudioDataSourceService(
            DataSourceService dataSourceService,
            ApiDataSourceRegistry apiDataSourceRegistry,
            DialectRegistry dialectRegistry,
            EngineDeploymentRepository deploymentRepository,
            EngineProperties properties
    ) {
        this.dataSourceService = dataSourceService;
        this.apiDataSourceRegistry = apiDataSourceRegistry;
        this.dialectRegistry = dialectRegistry;
        this.deploymentRepository = deploymentRepository;
        this.properties = properties;
    }

    public EngineDataSourceRegistrationResponse register(EngineDataSourceRegistrationRequest request) {
        if (!request.dataSourceId().equals(request.dataSource().dataSourceId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据源标识与快照不一致");
        }
        DBConfig config = toConfig(request.dataSource());
        try {
            dataSourceService.saveDBConfig(config);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法保存 API Studio 数据源", exception);
        }
        if (!apiDataSourceRegistry.contains(key(request.dataSourceId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "API Studio 数据源连接失败");
        }
        return new EngineDataSourceRegistrationResponse(
                properties.code(), request.dataSourceId(), EngineDataSourceStatus.READY, "数据源已注册"
        );
    }

    public EngineDataSourceTestResponse test(UUID dataSourceId) {
        DBConfig config = requireConfig(dataSourceId);
        try {
            dataSourceService.testDBConfig(config);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "数据源连接测试失败", exception);
        }
        return new EngineDataSourceTestResponse(properties.code(), dataSourceId, databaseType(config));
    }

    public EngineDataSourceRegistrationResponse remove(EngineDataSourceRemovalRequest request) {
        if (deploymentRepository.existsByEngineCodeAndDataSourceIdAndStatusIn(
                properties.code(), request.dataSourceId(), EnumSet.of(
                        EngineDeploymentRecordStatus.DEPLOYING,
                        EngineDeploymentRecordStatus.DEPLOYED,
                        EngineDeploymentRecordStatus.REMOVING,
                        EngineDeploymentRecordStatus.REMOVE_FAILED
                )
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仍有已部署服务正在使用该数据源");
        }
        DBConfig config = findConfig(request.dataSourceId());
        if (config == null) {
            return new EngineDataSourceRegistrationResponse(
                    properties.code(), request.dataSourceId(), EngineDataSourceStatus.REMOVED, "数据源不存在"
            );
        }
        try {
            dataSourceService.deleteDBConfig(config);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "无法删除 API Studio 数据源", exception);
        }
        return new EngineDataSourceRegistrationResponse(
                properties.code(), request.dataSourceId(), EngineDataSourceStatus.REMOVED, "数据源已移除"
        );
    }

    public RuntimeDataSource resolve(UUID dataSourceId) {
        DataSourceDialect dialect;
        try {
            dialect = apiDataSourceRegistry.require(key(dataSourceId));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务引用的数据源尚未注册到 API Studio", exception);
        }
        if (!(dialect instanceof JdbcDataSourceAccess jdbcDataSource)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "服务引用的 API Studio 数据源不是 JDBC 数据源");
        }
        return new RuntimeDataSource(databaseType(requireConfig(dataSourceId)), jdbcDataSource);
    }

    public Connection connection(UUID dataSourceId) throws SQLException {
        return connection(resolve(dataSourceId));
    }

    public Connection connection(RuntimeDataSource runtimeDataSource) throws SQLException {
        Connection connection = runtimeDataSource.jdbcDataSource().getDataSource().getConnection();
        try {
            connection.setReadOnly(true);
        } catch (SQLException ignored) {
            // Read-only mode is an optimization and a few JDBC drivers do not support it.
        }
        return connection;
    }

    private DBConfig toConfig(JdbcDataSourceSnapshot snapshot) {
        String databaseType = normalize(snapshot.databaseType());
        String driver = DRIVER_BY_DATABASE_TYPE.get(databaseType);
        if (driver == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API Studio 不支持该数据库类型");
        }
        DatabaseDialect dialect = dialectRegistry.require(databaseType);
        JdbcConnectionSpec spec = dialect.createConnectionSpec(new JdbcConnectionConfig(
                snapshot.host(), snapshot.port(), snapshot.databaseName(), snapshot.schemaName(), snapshot.username(),
                snapshot.password(), snapshot.options()
        ));
        Properties hikariProperties = new Properties();
        hikariProperties.setProperty("poolName", "data-scalpel-" + snapshot.dataSourceId());
        hikariProperties.setProperty("driverClassName", spec.driverClassName());
        hikariProperties.setProperty("minimumIdle", "0");
        hikariProperties.setProperty("maximumPoolSize", "10");
        hikariProperties.setProperty("connectionTimeout", "10000");
        hikariProperties.setProperty("validationTimeout", "5000");
        hikariProperties.setProperty("idleTimeout", "600000");
        hikariProperties.setProperty("maxLifetime", "1800000");

        String key = key(snapshot.dataSourceId());
        DBConfig config = DBConfig.builder()
                .driver(driver)
                .name(key)
                .comment("DataScalpel Service Engine")
                .url(spec.jdbcUrl())
                .user(snapshot.username())
                .password(snapshot.password())
                .enabled(true)
                .properties(hikariProperties)
                .dataSourceProperties(spec.properties())
                .build();
        config.setId(key);
        return config;
    }

    private DBConfig requireConfig(UUID dataSourceId) {
        DBConfig config = findConfig(dataSourceId);
        if (config == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "API Studio 数据源不存在");
        }
        return config;
    }

    private DBConfig findConfig(UUID dataSourceId) {
        try {
            return dataSourceService.getDBConfigById(key(dataSourceId));
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取 API Studio 数据源", exception);
        }
    }

    private static String databaseType(DBConfig config) {
        String databaseType = DATABASE_TYPE_BY_DRIVER.get(config.getDriver());
        if (databaseType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "API Studio 数据源驱动不受 Service Engine 支持");
        }
        return databaseType;
    }

    private static String key(UUID dataSourceId) {
        return dataSourceId.toString();
    }

    private static String normalize(String databaseType) {
        return databaseType == null ? "" : databaseType.trim().toUpperCase(Locale.ROOT);
    }

    public record RuntimeDataSource(String databaseType, JdbcDataSourceAccess jdbcDataSource) {
    }
}
