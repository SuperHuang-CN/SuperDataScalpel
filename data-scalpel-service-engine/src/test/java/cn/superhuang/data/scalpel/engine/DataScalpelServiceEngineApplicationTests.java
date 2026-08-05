package cn.superhuang.data.scalpel.engine;

import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationResponse;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRemovalRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceStatus;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceTestResponse;
import cn.superhuang.data.scalpel.contract.service.JdbcDataSourceSnapshot;
import cn.superhuang.data.scalpel.engine.config.EngineProperties;
import cn.superhuang.data.scalpel.engine.datasource.EngineApiStudioDataSourceService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(DataScalpelServiceEngineApplicationTests.TestDataSourceConfiguration.class)
class DataScalpelServiceEngineApplicationTests {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private TestApiStudioDataSourceService dataSourceService;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void managementTokenProtectsDeploymentsAndDeploymentRegistersAndRemovesPublicRoute() throws Exception {
        UUID serviceId = UUID.randomUUID();
        UUID dataSourceId = UUID.randomUUID();
        String registration = dataSourceRegistrationRequest(dataSourceId);
        String request = deploymentRequest(serviceId, dataSourceId, "/open-api/v1/orders");
        mockMvc.perform(post("/internal/v1/deployments").contentType("application/json").content(request))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:datascalpel:problem:authentication-required"))
                .andExpect(jsonPath("$.title").value("需要身份认证"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("缺少服务引擎管理令牌"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.instance").value("/internal/v1/deployments"))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(post("/internal/v1/data-sources")
                        .header("Authorization", "Bearer engine-test-token")
                        .contentType("application/json")
                        .content(registration))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        mockMvc.perform(post("/internal/v1/deployments")
                        .header("Authorization", "Bearer engine-test-token")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEPLOYED"));

        mockMvc.perform(post("/open-api/v1/orders")
                        .contentType("application/json")
                        .content("{\"pageNo\":1,\"pageSize\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:datascalpel:problem:invalid-query"))
                .andExpect(jsonPath("$.code").value("INVALID_QUERY"))
                .andExpect(jsonPath("$.instance").value("/open-api/v1/orders"));

        mockMvc.perform(post("/internal/v1/deployments/actions/remove")
                        .header("Authorization", "Bearer engine-test-token")
                        .contentType("application/json")
                        .content("{\"serviceId\":\"" + serviceId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED"));

        mockMvc.perform(post("/open-api/v1/orders").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sqlDeploymentExecutesNamedArgumentsPaginationAndCount() throws Exception {
        UUID serviceId = UUID.randomUUID();
        UUID dataSourceId = UUID.randomUUID();
        String routePath = "/open-api/v1/customers-" + serviceId.toString().substring(0, 8);

        mockMvc.perform(post("/internal/v1/data-sources")
                        .header("Authorization", "Bearer engine-test-token")
                        .contentType("application/json")
                        .content(dataSourceRegistrationRequest(dataSourceId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/internal/v1/deployments")
                        .header("Authorization", "Bearer engine-test-token")
                        .contentType("application/json")
                        .content(sqlDeploymentRequest(serviceId, dataSourceId, routePath)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEPLOYED"));

        mockMvc.perform(post(routePath)
                        .contentType("application/json")
                        .content("""
                                {
                                  "pageNo":1,
                                  "pageSize":1,
                                  "arguments":{"departmentId":1001,"keyword":null},
                                  "returnCount":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNo").value(1))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.resultList.length()").value(1))
                .andExpect(jsonPath("$.resultList[0].name").value("Alice"));

        mockMvc.perform(post(routePath)
                        .contentType("application/json")
                        .content("{\"arguments\":{\"unknown\":1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY"));

        mockMvc.perform(post("/internal/v1/deployments/actions/remove")
                        .header("Authorization", "Bearer engine-test-token")
                        .contentType("application/json")
                        .content("{\"serviceId\":\"" + serviceId + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void registrationPersistsAndReplacesJdbcConnectionOptions() throws Exception {
        UUID dataSourceId = UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/data-sources")
                        .header("Authorization", "Bearer engine-test-token")
                        .contentType("application/json")
                        .content(dataSourceRegistrationRequest(dataSourceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
        assertEquals(
                Map.of("sslmode", "prefer", "tcpKeepAlive", "true"),
                dataSourceService.requireSnapshot(dataSourceId).options()
        );

        mockMvc.perform(post("/internal/v1/data-sources")
                        .header("Authorization", "Bearer engine-test-token")
                        .contentType("application/json")
                        .content(dataSourceRegistrationRequest(dataSourceId, "false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
        assertEquals(
                Map.of("sslmode", "prefer", "tcpKeepAlive", "false"),
                dataSourceService.requireSnapshot(dataSourceId).options()
        );
    }

    private static String deploymentRequest(UUID serviceId, UUID dataSourceId, String routePath) {
        return """
                {
                  "serviceId":"%s",
                  "serviceCode":"orders",
                  "routePath":"%s",
                  "definitionDigest":"test-digest",
                  "definition":{
                    "type":"STANDARD_TABLE",
                    "standardDefinition":{
                      "protocolVersion":1,
                      "schemaName":"public",
                      "physicalTableName":"orders",
                      "fields":[
                        {"code":"id","physicalColumn":"id","type":"INTEGER","nullable":false,"primaryKey":true},
                        {"code":"name","physicalColumn":"name","type":"STRING","nullable":true,"primaryKey":false}
                      ]
                    },
                    "sqlDefinition":null
                  },
                  "dataSourceId":"%s"
                }
                """.formatted(serviceId, routePath, dataSourceId);
    }

    private static String dataSourceRegistrationRequest(UUID dataSourceId) {
        return dataSourceRegistrationRequest(dataSourceId, "true");
    }

    private static String dataSourceRegistrationRequest(UUID dataSourceId, String tcpKeepAlive) {
        return """
                {
                  "dataSourceId":"%s",
                  "dataSource":{
                    "dataSourceId":"%s",
                    "databaseType":"POSTGRESQL",
                    "host":"db.internal",
                    "port":5432,
                    "databaseName":"sample",
                    "username":"reader",
                    "password":"secret",
                    "options":{"sslmode":"prefer","tcpKeepAlive":"%s"}
                  }
                }
                """.formatted(dataSourceId, dataSourceId, tcpKeepAlive);
    }

    private static String sqlDeploymentRequest(UUID serviceId, UUID dataSourceId, String routePath) {
        return """
                {
                  "serviceId":"%s",
                  "serviceCode":"customer_query",
                  "routePath":"%s",
                  "definitionDigest":"sql-test-digest",
                  "definition":{
                    "type":"SQL_QUERY",
                    "standardDefinition":null,
                    "sqlDefinition":{
                      "protocolVersion":1,
                      "jdbcSql":"SELECT id, name FROM customer WHERE department_id = ? AND (? IS NULL OR name LIKE ?) ORDER BY id",
                      "bindingOrder":["departmentId","keyword","keyword"],
                      "parameters":[
                        {"name":"departmentId","typeDefinition":{"type":"LONG"},"required":true},
                        {"name":"keyword","typeDefinition":{"type":"STRING","length":100},"required":false}
                      ],
                      "resultFields":[
                        {"name":"id","typeDefinition":{"type":"LONG"},"nullable":true},
                        {"name":"name","typeDefinition":{"type":"STRING","length":100},"nullable":true}
                      ]
                    }
                  },
                  "dataSourceId":"%s"
                }
                """.formatted(serviceId, routePath, dataSourceId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestDataSourceConfiguration {

        @Bean
        @Primary
        TestApiStudioDataSourceService testApiStudioDataSourceService() {
            return new TestApiStudioDataSourceService();
        }
    }

    static class TestApiStudioDataSourceService extends EngineApiStudioDataSourceService {

        private final Map<UUID, JdbcDataSourceSnapshot> snapshots = new ConcurrentHashMap<>();
        private boolean initialized;

        TestApiStudioDataSourceService() {
            super(null, null, null, null, new EngineProperties("engine_test", "engine-test-token"));
        }

        @Override
        public EngineDataSourceRegistrationResponse register(EngineDataSourceRegistrationRequest request) {
            snapshots.put(request.dataSourceId(), request.dataSource());
            return new EngineDataSourceRegistrationResponse(
                    "engine_test", request.dataSourceId(), EngineDataSourceStatus.READY, "数据源已注册"
            );
        }

        @Override
        public EngineDataSourceTestResponse test(UUID dataSourceId) {
            requireSnapshot(dataSourceId);
            return new EngineDataSourceTestResponse("engine_test", dataSourceId, "POSTGRESQL");
        }

        @Override
        public EngineDataSourceRegistrationResponse remove(EngineDataSourceRemovalRequest request) {
            snapshots.remove(request.dataSourceId());
            return new EngineDataSourceRegistrationResponse(
                    "engine_test", request.dataSourceId(), EngineDataSourceStatus.REMOVED, "数据源已移除"
            );
        }

        @Override
        public RuntimeDataSource resolve(UUID dataSourceId) {
            requireSnapshot(dataSourceId);
            return new RuntimeDataSource("POSTGRESQL", null);
        }

        @Override
        public synchronized Connection connection(RuntimeDataSource runtimeDataSource) throws SQLException {
            Connection connection = DriverManager.getConnection(
                    "jdbc:h2:mem:engine_query;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""
            );
            if (!initialized) {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE customer (id BIGINT NOT NULL, name VARCHAR(100), department_id BIGINT NOT NULL)");
                    statement.execute("INSERT INTO customer VALUES (1, 'Alice', 1001), (2, 'Bob', 1001), (3, 'Carol', 1002)");
                }
                initialized = true;
            }
            connection.setReadOnly(true);
            return connection;
        }

        JdbcDataSourceSnapshot requireSnapshot(UUID dataSourceId) {
            JdbcDataSourceSnapshot snapshot = snapshots.get(dataSourceId);
            if (snapshot == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "测试数据源尚未注册");
            }
            return snapshot;
        }
    }
}
