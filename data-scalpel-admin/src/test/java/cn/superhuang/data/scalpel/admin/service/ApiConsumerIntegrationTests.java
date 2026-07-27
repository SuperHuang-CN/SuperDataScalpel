package cn.superhuang.data.scalpel.admin.service;

import cn.superhuang.data.scalpel.business.service.consumer.domain.GatewayConsumerSyncStatus;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerOperationException;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerInspectionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerPort;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerReference;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerResult;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerSpec;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import cn.superhuang.data.scalpel.business.service.consumer.repository.ApiConsumerRepository;
import cn.superhuang.data.scalpel.business.service.consumer.repository.GatewayConsumerBindingRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = "data-scalpel.service-gateway.provider=DATASCALPEL")
@Import(ApiConsumerIntegrationTests.FakeGatewayConfiguration.class)
class ApiConsumerIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;
    @Autowired
    private ApiConsumerRepository consumerRepository;
    @Autowired
    private GatewayConsumerBindingRepository bindingRepository;
    @Autowired
    private FakeGatewayConsumerPort gateway;

    private MockMvc mockMvc;
    private MockMvc unauthenticatedMockMvc;

    @BeforeEach
    void setUp() throws Exception {
        bindingRepository.deleteAllInBatch();
        consumerRepository.deleteAllInBatch();
        gateway.reset();

        unauthenticatedMockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        String token = loginAsAdministrator(unauthenticatedMockMvc);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .defaultRequest(get("/").header("Authorization", "Bearer " + token))
                .apply(springSecurity())
                .build();
    }

    @Test
    void managesConsumerLifecycleWithImmutableCodeSearchAndExternalCallsOutsideTransactions() throws Exception {
        String code = "customer." + suffix();
        String createdBody = mockMvc.perform(post("/api/v1/api-consumers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "name":"客户系统",
                                  "description":"初始说明"
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.gatewayBindings[0].provider").value("DATASCALPEL"))
                .andExpect(jsonPath("$.gatewayBindings[0].syncStatus").value("SYNCED"))
                .andExpect(jsonPath("$.gatewayBindings[0].syncedRevision").value(1))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(createdBody, "$.id"));

        mockMvc.perform(get("/api/v1/api-consumers")
                        .param("search", "code:\"" + code + "\"")
                        .param("sort", "-updatedAt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()));

        mockMvc.perform(post("/api/v1/api-consumers/{id}/actions/update", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"attempted-change",
                                  "name":"新客户系统",
                                  "description":"修改后的说明"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.name").value("新客户系统"))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.gatewayBindings[0].syncedRevision").value(2));

        assertThat(gateway.upserts).hasSize(2);
        assertThat(gateway.upserts.get(1).code()).isEqualTo(code);
        assertThat(gateway.upsertTransactionStates).containsOnly(false);

        mockMvc.perform(post("/api/v1/api-consumers/{id}/actions/delete", id))
                .andExpect(status().isNoContent());

        assertThat(consumerRepository.findById(id)).isEmpty();
        assertThat(bindingRepository.findAllByConsumerId(id)).isEmpty();
        assertThat(gateway.removeTransactionStates).containsOnly(false);
    }

    @Test
    void preservesFailedSynchronizationAndAllowsManualRecovery() throws Exception {
        gateway.failUpsert = true;
        String code = "failed." + suffix();
        String createdBody = mockMvc.perform(post("/api/v1/api-consumers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"失败后可恢复","description":null}
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gatewayBindings[0].syncStatus").value("SYNC_FAILED"))
                .andExpect(jsonPath("$.gatewayBindings[0].lastError").value("模拟网关同步失败"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(createdBody, "$.id"));

        assertThat(consumerRepository.findById(id)).isPresent();
        assertThat(bindingRepository.findAllByConsumerId(id))
                .singleElement()
                .extracting(binding -> binding.getSyncStatus())
                .isEqualTo(GatewayConsumerSyncStatus.SYNC_FAILED);

        gateway.failUpsert = false;
        mockMvc.perform(post("/api/v1/api-consumers/{id}/actions/sync", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayBindings[0].syncStatus").value("SYNCED"))
                .andExpect(jsonPath("$.gatewayBindings[0].lastError").isEmpty());
    }

    @Test
    void reconcilesConsumerReadOnlyAndLeavesRepairToExplicitSync() throws Exception {
        String createdBody = create("reconcile." + suffix(), "待对账消费者");
        UUID id = UUID.fromString(JsonPath.read(createdBody, "$.id"));
        gateway.inspectResult = GatewayInspectionResult.drifted(
                GatewayReconciliationReason.REMOTE_MISSING,
                "模拟远端 Consumer 丢失"
        );

        mockMvc.perform(post("/api/v1/api-consumers/{id}/actions/reconcile-gateway", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayBindings[0].syncStatus").value("SYNCED"))
                .andExpect(jsonPath("$.gatewayBindings[0].reconciliationStatus").value("DRIFTED"))
                .andExpect(jsonPath("$.gatewayBindings[0].reconciliationReason").value("REMOTE_MISSING"))
                .andExpect(jsonPath("$.gatewayBindings[0].reconciliationMessage")
                        .value("模拟远端 Consumer 丢失"))
                .andExpect(jsonPath("$.gatewayBindings[0].lastReconciledAt").isNotEmpty());

        assertThat(gateway.inspectTransactionStates).containsOnly(false);
        assertThat(gateway.upserts).hasSize(1);

        mockMvc.perform(post("/api/v1/api-consumers/{id}/actions/sync", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayBindings[0].syncStatus").value("SYNCED"))
                .andExpect(jsonPath("$.gatewayBindings[0].reconciliationStatus").value("NOT_CHECKED"));
        assertThat(gateway.upserts).hasSize(2);
    }

    @Test
    void reportsStableProblemsForDuplicateValidationAuthenticationAndRetryableDeleteFailure() throws Exception {
        String code = "protected." + suffix();
        String createdBody = create(code, "受保护消费者");
        UUID id = UUID.fromString(JsonPath.read(createdBody, "$.id"));

        mockMvc.perform(post("/api/v1/api-consumers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"重复消费者"}
                                """.formatted(code)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"))
                .andExpect(jsonPath("$.detail").value("消费者编码已存在"));

        mockMvc.perform(post("/api/v1/api-consumers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"INVALID/CODE","name":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.violations").isArray());

        unauthenticatedMockMvc.perform(get("/api/v1/api-consumers"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        gateway.failRemove = true;
        mockMvc.perform(post("/api/v1/api-consumers/{id}/actions/delete", id))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("网关消费者删除失败：DATASCALPEL：模拟网关删除失败"));

        assertThat(consumerRepository.findById(id)).isPresent();
        assertThat(bindingRepository.findAllByConsumerId(id))
                .singleElement()
                .extracting(binding -> binding.getSyncStatus())
                .isEqualTo(GatewayConsumerSyncStatus.DELETE_FAILED);

        gateway.failRemove = false;
        mockMvc.perform(post("/api/v1/api-consumers/{id}/actions/delete", id))
                .andExpect(status().isNoContent());
        assertThat(consumerRepository.findById(id)).isEmpty();
    }

    private String create(String code, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/api-consumers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"%s"}
                                """.formatted(code, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @TestConfiguration
    static class FakeGatewayConfiguration {

        @Bean
        FakeGatewayConsumerPort fakeGatewayConsumerPort() {
            return new FakeGatewayConsumerPort();
        }
    }

    static class FakeGatewayConsumerPort implements GatewayConsumerPort {

        private final List<GatewayConsumerSpec> upserts = new ArrayList<>();
        private final List<Boolean> upsertTransactionStates = new ArrayList<>();
        private final List<Boolean> removeTransactionStates = new ArrayList<>();
        private final List<Boolean> inspectTransactionStates = new ArrayList<>();
        private boolean failUpsert;
        private boolean failRemove;
        private GatewayInspectionResult inspectResult =
                GatewayInspectionResult.inSync("模拟消费者状态一致");

        @Override
        public GatewayProvider provider() {
            return GatewayProvider.DATASCALPEL;
        }

        @Override
        public GatewayConsumerResult upsert(GatewayConsumerSpec consumer) {
            upserts.add(consumer);
            upsertTransactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (failUpsert) {
                throw new GatewayConsumerOperationException("模拟网关同步失败");
            }
            return new GatewayConsumerResult("fake-" + consumer.id());
        }

        @Override
        public void remove(GatewayConsumerReference consumer) {
            removeTransactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (failRemove) {
                throw new GatewayConsumerOperationException("模拟网关删除失败");
            }
        }

        @Override
        public GatewayInspectionResult inspect(GatewayConsumerInspectionSpec consumer) {
            inspectTransactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            return inspectResult;
        }

        void reset() {
            upserts.clear();
            upsertTransactionStates.clear();
            removeTransactionStates.clear();
            inspectTransactionStates.clear();
            failUpsert = false;
            failRemove = false;
            inspectResult = GatewayInspectionResult.inSync("模拟消费者状态一致");
        }
    }
}
