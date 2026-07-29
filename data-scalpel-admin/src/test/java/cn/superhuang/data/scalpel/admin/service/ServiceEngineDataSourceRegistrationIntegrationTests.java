package cn.superhuang.data.scalpel.admin.service;

import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.service.ServiceEngineClient;
import cn.superhuang.data.scalpel.business.service.ServiceEngineCredentialCipher;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineDataSourceRegistrationRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineRepository;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationResponse;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRemovalRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceStatus;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceTestResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceEngineInfoResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceUndeploymentRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Import(ServiceEngineDataSourceRegistrationIntegrationTests.EngineClientConfiguration.class)
class ServiceEngineDataSourceRegistrationIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private ServiceEngineDataSourceRegistrationRepository registrationRepository;

    @Autowired
    private ServiceEngineRepository engineRepository;

    @Autowired
    private DataSourceRepository dataSourceRepository;

    private MockMvc mockMvc;
    private String createdRegistrationId;
    private String createdEngineId;
    private String createdDataSourceId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        String token = loginAsAdministrator(mockMvc);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .defaultRequest(get("/").header("Authorization", "Bearer " + token))
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        if (createdRegistrationId != null) {
            registrationRepository.deleteById(UUID.fromString(createdRegistrationId));
        }
        if (createdEngineId != null) {
            engineRepository.deleteById(UUID.fromString(createdEngineId));
        }
        if (createdDataSourceId != null) {
            dataSourceRepository.deleteById(UUID.fromString(createdDataSourceId));
        }
    }

    @Test
    void registersSynchronizesAndProtectsAnEngineDataSourceRelationship() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String dataSourceId = createDataSource("runtime_" + suffix);
        String engineId = createEngine("engine_" + suffix);
        createdDataSourceId = dataSourceId;
        createdEngineId = engineId;

        String registration = mockMvc.perform(post("/api/v1/service-engine-data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"engineId\":\"" + engineId + "\",\"dataSourceId\":\"" + dataSourceId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.revision").value(1))
                .andReturn().getResponse().getContentAsString();
        String registrationId = com.jayway.jsonpath.JsonPath.read(registration, "$.id");
        createdRegistrationId = registrationId;

        mockMvc.perform(post("/api/v1/data-sources/{id}/actions/update", dataSourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"运行库（更新）",
                                  "purposes":["STORAGE"],
                                  "type":"POSTGRESQL",
                                  "enabled":true,
                                  "connection":{
                                    "kind":"JDBC",
                                    "host":"192.168.20.11",
                                    "port":5432,
                                    "databaseName":"runtime",
                                    "schemaName":"public",
                                    "username":"reader",
                                    "options":{
                                      "sslmode":"require",
                                      "tcpKeepAlive":"false"
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/service-engine-data-sources/{id}", registrationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUTDATED"));

        mockMvc.perform(post("/api/v1/service-engine-data-sources/{id}/actions/sync", registrationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.revision").value(2));

        mockMvc.perform(post("/api/v1/service-engine-data-sources/{id}/actions/test", registrationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseType").value("POSTGRESQL"));

        mockMvc.perform(post("/api/v1/data-sources/{id}/actions/delete", dataSourceId))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/service-engine-data-sources/{id}/actions/delete", registrationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent());
        createdRegistrationId = null;

        mockMvc.perform(post("/api/v1/data-sources/{id}/actions/delete", dataSourceId))
                .andExpect(status().isNoContent());
        createdDataSourceId = null;
        mockMvc.perform(post("/api/v1/service-engines/{id}/actions/delete", engineId))
                .andExpect(status().isNoContent());
        createdEngineId = null;
    }

    private String createDataSource(String code) throws Exception {
        String response = mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "name":"运行库",
                                  "purposes":["STORAGE"],
                                  "type":"POSTGRESQL",
                                  "enabled":true,
                                  "connection":{
                                    "kind":"JDBC",
                                    "host":"192.168.20.11",
                                    "port":5432,
                                    "databaseName":"runtime",
                                    "schemaName":"public",
                                    "username":"reader",
                                    "password":"secret",
                                    "options":{
                                      "sslmode":"prefer",
                                      "tcpKeepAlive":"true"
                                    }
                                  }
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    private String createEngine(String code) throws Exception {
        String response = mockMvc.perform(post("/api/v1/service-engines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"测试 Engine",
                                  "adminUrl":"http://engine.test:8081",
                                  "publicUrl":"http://engine.test:8081",
                                  "managementToken":"engine-test-token:%s",
                                  "enabled":true
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EngineClientConfiguration {

        @Bean
        @Primary
        ServiceEngineClient serviceEngineClient(ServiceEngineCredentialCipher credentialCipher) {
            return new ServiceEngineClient(credentialCipher) {
                @Override
                public ServiceEngineInfoResponse info(String adminUrl, String managementToken) {
                    assertNoManagementTransaction();
                    return new ServiceEngineInfoResponse(
                            managementToken.substring("engine-test-token:".length()),
                            List.of("POSTGRESQL")
                    );
                }

                @Override
                public ServiceEngineInfoResponse info(ServiceEngine engine) {
                    assertNoManagementTransaction();
                    return new ServiceEngineInfoResponse(engine.getCode(), List.of("POSTGRESQL"));
                }

                @Override
                public EngineDataSourceRegistrationResponse registerDataSource(
                        ServiceEngine engine,
                        EngineDataSourceRegistrationRequest request
                ) {
                    assertNoManagementTransaction();
                    Map<String, String> expectedOptions = request.revision() == 1
                            ? Map.of("sslmode", "prefer", "tcpKeepAlive", "true")
                            : Map.of("sslmode", "require", "tcpKeepAlive", "false");
                    assertEquals(expectedOptions, request.dataSource().options());
                    return new EngineDataSourceRegistrationResponse(
                            engine.getCode(), request.dataSourceId(), request.revision(), EngineDataSourceStatus.READY, "已注册"
                    );
                }

                @Override
                public EngineDataSourceTestResponse testDataSource(ServiceEngine engine, UUID dataSourceId) {
                    assertNoManagementTransaction();
                    return new EngineDataSourceTestResponse(engine.getCode(), dataSourceId, 2, "POSTGRESQL");
                }

                @Override
                public EngineDataSourceRegistrationResponse removeDataSource(
                        ServiceEngine engine,
                        EngineDataSourceRemovalRequest request
                ) {
                    assertNoManagementTransaction();
                    return new EngineDataSourceRegistrationResponse(
                            engine.getCode(), request.dataSourceId(), request.revision(), EngineDataSourceStatus.REMOVED, "已移除"
                    );
                }

                @Override
                public ServiceDeploymentResponse deploy(ServiceEngine engine, ServiceDeploymentRequest request) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public ServiceDeploymentResponse remove(ServiceEngine engine, ServiceUndeploymentRequest request) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        private static void assertNoManagementTransaction() {
            assertFalse(
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    "Engine HTTP 调用不得处于管理数据库事务中"
            );
        }
    }
}
