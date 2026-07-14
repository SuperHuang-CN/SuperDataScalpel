package cn.superhuang.data.scalpel.admin.datasource;

import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.dialect.model.ConnectionCheck;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Import(DataSourceIntegrationTests.StubDatabaseInspectorConfiguration.class)
class DataSourceIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private DataSourceRepository repository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        String accessToken = loginAsAdministrator(mockMvc);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .defaultRequest(get("/").header("Authorization", "Bearer " + accessToken))
                .apply(springSecurity())
                .build();
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void createsSearchesUpdatesAndDeletesDataSourcesWithoutReturningPasswords() throws Exception {
        String created = mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "Business_PostgreSQL",
                                  "name": "业务 PostgreSQL",
                                  "purposes": ["SOURCE", "STORAGE", "DISTRIBUTION"],
                                  "databaseType": "POSTGRESQL",
                                  "enabled": true,
                                  "description": "业务主库",
                                  "connection": {
                                    "host": "192.168.1.10",
                                    "port": 5432,
                                    "databaseName": "business",
                                    "schemaName": "public",
                                    "username": "datascalpel",
                                    "password": "secret-value",
                                    "options": {
                                      "sslmode": "prefer"
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("business_postgresql"))
                .andExpect(jsonPath("$.purposes.length()").value(3))
                .andExpect(jsonPath("$.connection.passwordConfigured").value(true))
                .andExpect(jsonPath("$.connection.options.sslmode").value("prefer"))
                .andExpect(jsonPath("$.connection.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/v1/data-sources")
                        .param("search", "sourceEnabled:\"true\" AND storageEnabled:\"true\" AND distributionEnabled:\"true\"")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "-updatedAt,code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("business_postgresql"));

        mockMvc.perform(post("/api/v1/data-sources/{id}/actions/update", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "业务 PostgreSQL（已更新）",
                                  "purposes": ["STORAGE"],
                                  "databaseType": "POSTGRESQL",
                                  "enabled": false,
                                  "description": "更新后说明",
                                  "connection": {
                                    "host": "192.168.1.11",
                                    "port": 5432,
                                    "databaseName": "business",
                                    "schemaName": "public",
                                    "username": "datascalpel"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("business_postgresql"))
                .andExpect(jsonPath("$.purposes.length()").value(1))
                .andExpect(jsonPath("$.purposes[0]").value("STORAGE"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.connection.host").value("192.168.1.11"))
                .andExpect(jsonPath("$.connection.options.sslmode").value("prefer"))
                .andExpect(jsonPath("$.connection.passwordConfigured").value(true));

        mockMvc.perform(post("/api/v1/data-sources/{id}/actions/delete", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/data-sources/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesInputRejectsDuplicateCodesAndKeepsTheTestApiStable() throws Exception {
        mockMvc.perform(get("/api/v1/data-source-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[?(@.id == 'POSTGRESQL')].defaultPort").value(hasItem(5432)));

        String createRequest = """
                {
                  "code": "warehouse",
                  "name": "数仓",
                  "purposes": ["STORAGE"],
                  "databaseType": "DAMENG",
                  "connection": {
                    "host": "localhost",
                    "port": 5236,
                    "databaseName": "warehouse",
                    "username": "system"
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "invalid",
                                  "name": "无用途连接",
                                  "purposes": [],
                                  "databaseType": "MYSQL",
                                  "connection": {
                                    "host": "localhost",
                                    "port": 3306,
                                    "databaseName": "test",
                                    "username": "root"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/v1/data-sources/actions/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "databaseType": "MYSQL",
                                  "connection": {
                                    "host": "localhost",
                                    "port": 3306,
                                    "databaseName": "test",
                                    "username": "root"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.databaseProduct").value("Test Database"))
                .andExpect(jsonPath("$.message").value("连接测试成功"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubDatabaseInspectorConfiguration {

        @Bean
        @Primary
        DatabaseInspector stubDatabaseInspector() {
            return new DatabaseInspector(BuiltInDialects.registry(), new JdbcConnectionFactory()) {
                @Override
                public ConnectionCheck test(String databaseType, JdbcConnectionConfig config) {
                    return new ConnectionCheck(
                            true,
                            "SUCCESS",
                            "连接测试成功",
                            12,
                            "Test Database",
                            "1.0",
                            "Test JDBC Driver"
                    );
                }
            };
        }
    }
}
