package cn.superhuang.data.scalpel.engine;

import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.engine.query.DataSourcePoolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

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
                        .content("{\"serviceId\":\"" + serviceId + "\",\"revision\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED"));

        mockMvc.perform(post("/open-api/v1/orders").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }

    private static String deploymentRequest(UUID serviceId, UUID dataSourceId, String routePath) {
        return """
                {
                  "serviceId":"%s",
                  "revision":1,
                  "serviceCode":"orders",
                  "routePath":"%s",
                  "definitionDigest":"test-digest",
                  "definition":{
                    "protocolVersion":1,
                    "schemaName":"public",
                    "physicalTableName":"orders",
                    "fields":[
                      {"code":"id","physicalColumn":"id","type":"INTEGER","nullable":false,"primaryKey":true},
                      {"code":"name","physicalColumn":"name","type":"STRING","nullable":true,"primaryKey":false}
                    ]
                  },
                  "dataSourceId":"%s"
                }
                """.formatted(serviceId, routePath, dataSourceId);
    }

    private static String dataSourceRegistrationRequest(UUID dataSourceId) {
        return """
                {
                  "dataSourceId":"%s",
                  "revision":1,
                  "dataSource":{
                    "dataSourceId":"%s",
                    "databaseType":"POSTGRESQL",
                    "host":"db.internal",
                    "port":5432,
                    "databaseName":"sample",
                    "username":"reader",
                    "password":"secret"
                  }
                }
                """.formatted(dataSourceId, dataSourceId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestDataSourceConfiguration {

        @Bean
        @Primary
        DataSourcePoolRegistry testDataSourcePoolRegistry(DialectRegistry dialectRegistry) {
            return new DataSourcePoolRegistry(dialectRegistry) {
                @Override
                public void test(cn.superhuang.data.scalpel.contract.service.JdbcDataSourceSnapshot snapshot) {
                    // Engine registration behavior is tested without requiring an external PostgreSQL process.
                }
            };
        }
    }
}
