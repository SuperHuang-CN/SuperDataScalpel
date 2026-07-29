package cn.superhuang.data.scalpel.admin.service;

import cn.superhuang.data.scalpel.business.service.ServiceEngineClient;
import cn.superhuang.data.scalpel.business.service.ServiceEngineCredentialCipher;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineRepository;
import cn.superhuang.data.scalpel.contract.service.ServiceEngineInfoResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Import(ServiceEngineManagementIntegrationTests.EngineClientConfiguration.class)
class ServiceEngineManagementIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private ServiceEngineRepository repository;

    @Autowired
    private ServiceEngineCredentialCipher credentialCipher;

    @Autowired
    private RecordingServiceEngineClient engineClient;

    private final List<UUID> engineIds = new ArrayList<>();
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
        engineClient.reset();
    }

    @AfterEach
    void tearDown() {
        engineIds.forEach(repository::deleteById);
        engineIds.clear();
    }

    @Test
    void encryptsPerEngineTokensAndPreservesOrReplacesThemOnUpdate() throws Exception {
        String suffix = suffix();
        String firstId = createEngine("token_a_" + suffix, "token-a");
        String secondId = createEngine("token_b_" + suffix, "token-b");
        UUID firstUuid = UUID.fromString(firstId);
        UUID secondUuid = UUID.fromString(secondId);
        engineIds.add(firstUuid);
        engineIds.add(secondUuid);

        ServiceEngine first = repository.findById(firstUuid).orElseThrow();
        ServiceEngine second = repository.findById(secondUuid).orElseThrow();
        assertThat(first.getManagementTokenCiphertext()).doesNotContain("token-a");
        assertThat(second.getManagementTokenCiphertext()).doesNotContain("token-b");
        assertThat(first.getManagementTokenCiphertext()).isNotEqualTo(second.getManagementTokenCiphertext());
        assertThat(credentialCipher.decrypt(first.getManagementTokenCiphertext())).isEqualTo("token-a");
        assertThat(credentialCipher.decrypt(second.getManagementTokenCiphertext())).isEqualTo("token-b");

        String originalCiphertext = first.getManagementTokenCiphertext();
        engineClient.reportedCode = first.getCode();
        mockMvc.perform(post("/api/v1/service-engines/{id}/actions/update", firstId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest("Engine A", "http://engine-a.test:8081", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managementTokenConfigured").value(true))
                .andExpect(jsonPath("$.managementToken").doesNotExist());
        assertThat(repository.findById(firstUuid).orElseThrow().getManagementTokenCiphertext())
                .isEqualTo(originalCiphertext);

        engineClient.reportedCode = first.getCode();
        mockMvc.perform(post("/api/v1/service-engines/{id}/actions/update", firstId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest("Engine A", "http://engine-a.test:8081", "token-a-rotated")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managementTokenConfigured").value(true));
        String rotatedCiphertext = repository.findById(firstUuid).orElseThrow().getManagementTokenCiphertext();
        assertThat(rotatedCiphertext).isNotEqualTo(originalCiphertext);
        assertThat(credentialCipher.decrypt(rotatedCiphertext)).isEqualTo("token-a-rotated");
    }

    @Test
    void discoversCodeWhenCreatingOutsideTransactionsAndRejectsDuplicateOrInvalidCodes() throws Exception {
        long countBefore = repository.count();
        String code = "discovered_" + suffix();
        String engineId = createEngine(code, "discovery-token");
        UUID engineUuid = UUID.fromString(engineId);
        engineIds.add(engineUuid);

        assertThat(repository.findById(engineUuid).orElseThrow().getCode()).isEqualTo(code);
        assertThat(engineClient.adminUrl).isEqualTo("http://engine.test:8081");
        assertThat(engineClient.managementToken).isEqualTo("discovery-token");
        assertFalse(engineClient.transactionActive);

        engineClient.reportedCode = code;
        mockMvc.perform(post("/api/v1/service-engines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"重复 Engine",
                                  "adminUrl":"http://duplicate.test:8081",
                                  "publicUrl":"http://duplicate.test:8081",
                                  "managementToken":"duplicate-token",
                                  "enabled":true
                                }
                                """))
                .andExpect(status().isConflict());

        engineClient.reportedCode = "invalid-code";
        mockMvc.perform(post("/api/v1/service-engines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"非法 Engine",
                                  "adminUrl":"http://invalid.test:8081",
                                  "publicUrl":"http://invalid.test:8081",
                                  "managementToken":"invalid-token",
                                  "enabled":true
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Service Engine 返回的 Code 不符合编码规范"));

        assertThat(repository.count()).isEqualTo(countBefore + 1);
    }

    @Test
    void testsUnsavedAndStoredCandidatesOutsideTransactionsAndRejectsCodeMismatch() throws Exception {
        long countBefore = repository.count();
        engineClient.reportedCode = "candidate_" + suffix();
        String candidateCode = engineClient.reportedCode;

        mockMvc.perform(post("/api/v1/service-engines/actions/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adminUrl":"http://candidate.test:8081",
                                  "managementToken":"candidate-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(candidateCode))
                .andExpect(jsonPath("$.databaseTypes[0]").value("POSTGRESQL"))
                .andExpect(jsonPath("$.elapsedMs").isNumber());
        assertThat(repository.count()).isEqualTo(countBefore);
        assertThat(engineClient.adminUrl).isEqualTo("http://candidate.test:8081");
        assertThat(engineClient.managementToken).isEqualTo("candidate-token");
        assertFalse(engineClient.transactionActive);

        String storedCode = "stored_" + suffix();
        String engineId = createEngine(storedCode, "stored-token");
        UUID engineUuid = UUID.fromString(engineId);
        engineIds.add(engineUuid);
        engineClient.reportedCode = storedCode;

        mockMvc.perform(post("/api/v1/service-engines/{id}/actions/test", engineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adminUrl":"http://candidate-update.test:8081",
                                  "managementToken":"candidate-update-token"
                                }
                                """))
                .andExpect(status().isOk());
        assertThat(engineClient.adminUrl).isEqualTo("http://candidate-update.test:8081");
        assertThat(engineClient.managementToken).isEqualTo("candidate-update-token");

        mockMvc.perform(post("/api/v1/service-engines/{id}/actions/test", engineId))
                .andExpect(status().isOk());
        assertThat(engineClient.adminUrl).isEqualTo("http://engine.test:8081");
        assertThat(engineClient.managementToken).isEqualTo("stored-token");

        engineClient.reportedCode = "another_engine";
        mockMvc.perform(post("/api/v1/service-engines/{id}/actions/test", engineId))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(
                        "Service Engine Code 不一致，期望 %s，实际 another_engine".formatted(storedCode)
                ));
    }

    @Test
    void validatesIdentityChangesBeforeSavingAndSkipsProbeForOrdinaryUpdates() throws Exception {
        String code = "update_" + suffix();
        String engineId = createEngine(code, "stored-token");
        UUID engineUuid = UUID.fromString(engineId);
        engineIds.add(engineUuid);
        int callsAfterCreate = engineClient.infoCalls;

        mockMvc.perform(post("/api/v1/service-engines/{id}/actions/update", engineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest("仅修改名称", "http://engine.test:8081", "")))
                .andExpect(status().isOk());
        assertThat(engineClient.infoCalls).isEqualTo(callsAfterCreate);

        ServiceEngine beforeRejectedUpdate = repository.findById(engineUuid).orElseThrow();
        String originalCiphertext = beforeRejectedUpdate.getManagementTokenCiphertext();
        engineClient.reportedCode = "another_engine";
        mockMvc.perform(post("/api/v1/service-engines/{id}/actions/update", engineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest("不应保存", "http://other.test:8081", "candidate-token")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(
                        "Service Engine Code 不一致，期望 %s，实际 another_engine".formatted(code)
                ));

        ServiceEngine afterRejectedUpdate = repository.findById(engineUuid).orElseThrow();
        assertThat(afterRejectedUpdate.getName()).isEqualTo("仅修改名称");
        assertThat(afterRejectedUpdate.getAdminUrl()).isEqualTo("http://engine.test:8081");
        assertThat(afterRejectedUpdate.getManagementTokenCiphertext()).isEqualTo(originalCiphertext);
        assertThat(engineClient.adminUrl).isEqualTo("http://other.test:8081");
        assertThat(engineClient.managementToken).isEqualTo("candidate-token");
        assertFalse(engineClient.transactionActive);
    }

    @Test
    void requiresTokenWhenCreatingAnEngine() throws Exception {
        mockMvc.perform(post("/api/v1/service-engines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"缺少 Token",
                                  "adminUrl":"http://engine.test:8081",
                                  "publicUrl":"http://engine.test:8081",
                                  "enabled":true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("managementToken"));
    }

    @Test
    void reportsAuthenticationFailuresWithoutExposingRemoteBodies() throws Exception {
        long countBefore = repository.count();
        engineClient.failure = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                null,
                "remote-secret-body".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        mockMvc.perform(post("/api/v1/service-engines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"认证失败 Engine",
                                  "adminUrl":"http://candidate.test:8081",
                                  "publicUrl":"http://candidate.test:8081",
                                  "managementToken":"wrong-token",
                                  "enabled":true
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(
                        "Service Engine 拒绝访问，请检查 Management Token"
                ))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("remote-secret-body"))
                ));
        assertThat(repository.count()).isEqualTo(countBefore);

        engineClient.failure = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                null,
                "another-secret-body".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        mockMvc.perform(post("/api/v1/service-engines/actions/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adminUrl":"http://candidate.test:8081",
                                  "managementToken":"candidate-token"
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Service Engine 返回异常状态：HTTP 500"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("another-secret-body"))
                ));
    }

    private String createEngine(String code, String managementToken) throws Exception {
        engineClient.reportedCode = code;
        String response = mockMvc.perform(post("/api/v1/service-engines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"测试 Engine",
                                  "adminUrl":"http://engine.test:8081",
                                  "publicUrl":"http://engine.test:8081",
                                  "managementToken":"%s",
                                  "enabled":true
                                }
                                """.formatted(managementToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.managementTokenConfigured").value(true))
                .andExpect(jsonPath("$.managementToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    private static String updateRequest(String name, String adminUrl, String managementToken) {
        return """
                {
                  "name":"%s",
                  "adminUrl":"%s",
                  "publicUrl":"http://public.test:8081",
                  "managementToken":"%s",
                  "enabled":true
                }
                """.formatted(name, adminUrl, managementToken);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EngineClientConfiguration {

        @Bean
        @Primary
        RecordingServiceEngineClient recordingServiceEngineClient(ServiceEngineCredentialCipher credentialCipher) {
            return new RecordingServiceEngineClient(credentialCipher);
        }
    }

    static class RecordingServiceEngineClient extends ServiceEngineClient {

        private String reportedCode;
        private String adminUrl;
        private String managementToken;
        private boolean transactionActive;
        private RuntimeException failure;
        private int infoCalls;

        RecordingServiceEngineClient(ServiceEngineCredentialCipher credentialCipher) {
            super(credentialCipher);
        }

        @Override
        public ServiceEngineInfoResponse info(String adminUrl, String managementToken) {
            infoCalls++;
            if (failure != null) {
                throw failure;
            }
            this.adminUrl = adminUrl;
            this.managementToken = managementToken;
            this.transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            return new ServiceEngineInfoResponse(reportedCode, List.of("POSTGRESQL"));
        }

        void reset() {
            reportedCode = null;
            adminUrl = null;
            managementToken = null;
            transactionActive = false;
            failure = null;
            infoCalls = 0;
        }
    }
}
