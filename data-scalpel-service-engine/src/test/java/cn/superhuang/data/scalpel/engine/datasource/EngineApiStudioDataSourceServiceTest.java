package cn.superhuang.data.scalpel.engine.datasource;

import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceStatus;
import cn.superhuang.data.scalpel.contract.service.JdbcDataSourceSnapshot;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.engine.config.EngineProperties;
import cn.superhuang.data.scalpel.engine.deployment.EngineDeploymentRepository;
import cn.superhuang.superops.api.studio.datasource.ApiDataSourceRegistry;
import cn.superhuang.superops.api.studio.datasource.ClickHouseDataSource;
import cn.superhuang.superops.api.studio.datasource.application.DataSourceService;
import cn.superhuang.superops.api.studio.datasource.factory.ClickHouseDriver;
import cn.superhuang.superops.api.studio.datasource.factory.DmDriver;
import cn.superhuang.superops.api.studio.datasource.factory.KingBaseDriver;
import cn.superhuang.superops.api.studio.datasource.factory.MySQLDriver;
import cn.superhuang.superops.api.studio.datasource.factory.OpenGaussDriver;
import cn.superhuang.superops.api.studio.datasource.factory.OracleDriver;
import cn.superhuang.superops.api.studio.datasource.factory.PostgreSQLDriver;
import cn.superhuang.superops.api.studio.datasource.factory.SQLServerDriver;
import cn.superhuang.superops.api.studio.entity.DBConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EngineApiStudioDataSourceServiceTest {

    private static final Map<String, Integer> PORTS = Map.of(
            "POSTGRESQL", 5432,
            "MYSQL", 3306,
            "ORACLE", 1521,
            "SQL_SERVER", 1433,
            "CLICKHOUSE", 8123,
            "DAMENG", 5236,
            "KINGBASE", 54321,
            "OPENGAUSS", 5432
    );
    private static final Map<String, String> DRIVERS = Map.of(
            "POSTGRESQL", PostgreSQLDriver.class.getName(),
            "MYSQL", MySQLDriver.class.getName(),
            "ORACLE", OracleDriver.class.getName(),
            "SQL_SERVER", SQLServerDriver.class.getName(),
            "CLICKHOUSE", ClickHouseDriver.class.getName(),
            "DAMENG", DmDriver.class.getName(),
            "KINGBASE", KingBaseDriver.class.getName(),
            "OPENGAUSS", OpenGaussDriver.class.getName()
    );

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private ApiDataSourceRegistry apiDataSourceRegistry;

    @Mock
    private EngineDeploymentRepository deploymentRepository;

    @Test
    void mapsAllSupportedDatabasesAndSeparatesJdbcFromHikariProperties() throws Exception {
        when(apiDataSourceRegistry.contains(anyString())).thenReturn(true);
        EngineApiStudioDataSourceService service = new EngineApiStudioDataSourceService(
                dataSourceService,
                apiDataSourceRegistry,
                BuiltInDialects.registry(),
                deploymentRepository,
                new EngineProperties("engine_test", "token")
        );
        Map<String, UUID> ids = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> database : PORTS.entrySet()) {
            UUID dataSourceId = UUID.randomUUID();
            ids.put(database.getKey(), dataSourceId);
            Map<String, String> options = "POSTGRESQL".equals(database.getKey())
                    ? Map.of("sslmode", "prefer")
                    : Map.of();
            JdbcDataSourceSnapshot snapshot = new JdbcDataSourceSnapshot(
                    dataSourceId,
                    database.getKey(),
                    "db.internal",
                    database.getValue(),
                    "sample",
                    "public",
                    "reader",
                    "secret",
                    options
            );

            var response = service.register(new EngineDataSourceRegistrationRequest(dataSourceId, snapshot));

            assertEquals(EngineDataSourceStatus.READY, response.status());
            assertEquals(dataSourceId, response.dataSourceId());
        }

        ArgumentCaptor<DBConfig> configs = ArgumentCaptor.forClass(DBConfig.class);
        verify(dataSourceService, times(PORTS.size())).saveDBConfig(configs.capture());
        Map<String, DBConfig> byId = configs.getAllValues().stream()
                .collect(java.util.stream.Collectors.toMap(DBConfig::getId, config -> config));

        ids.forEach((databaseType, dataSourceId) -> {
            DBConfig config = byId.get(dataSourceId.toString());
            assertNotNull(config);
            assertEquals(dataSourceId.toString(), config.getName());
            assertEquals(DRIVERS.get(databaseType), config.getDriver());
            assertFalse(config.getUrl().isBlank());
            assertEquals("10", config.getProperties().getProperty("maximumPoolSize"));
            assertEquals("10000", config.getProperties().getProperty("connectionTimeout"));
            assertNotNull(config.getProperties().getProperty("driverClassName"));
        });

        DBConfig postgresql = byId.get(ids.get("POSTGRESQL").toString());
        assertEquals("prefer", postgresql.getDataSourceProperties().getProperty("sslmode"));
        assertNull(postgresql.getProperties().getProperty("sslmode"));
    }

    @Test
    void resolvesClickHouseAsNonTransactionalJdbcDatasource() throws Exception {
        UUID dataSourceId = UUID.randomUUID();
        DBConfig config = DBConfig.builder().driver(ClickHouseDriver.class.getName()).build();
        ClickHouseDataSource clickHouse = new ClickHouseDataSource();
        when(apiDataSourceRegistry.require(dataSourceId.toString())).thenReturn(clickHouse);
        when(dataSourceService.getDBConfigById(dataSourceId.toString())).thenReturn(config);

        EngineApiStudioDataSourceService service = new EngineApiStudioDataSourceService(
                dataSourceService,
                apiDataSourceRegistry,
                BuiltInDialects.registry(),
                deploymentRepository,
                new EngineProperties("engine_test", "token")
        );

        var runtimeDataSource = service.resolve(dataSourceId);

        assertEquals("CLICKHOUSE", runtimeDataSource.databaseType());
        assertInstanceOf(ClickHouseDataSource.class, runtimeDataSource.jdbcDataSource());
    }
}
