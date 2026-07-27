package cn.superhuang.data.scalpel.admin.service;

import cn.superhuang.data.scalpel.business.service.consumer.credential.domain.GatewayCredentialStatus;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialOperationException;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialInspectionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialPort;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialReference;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialResult;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialSpec;
import cn.superhuang.data.scalpel.business.service.consumer.credential.repository.ApiConsumerCredentialRepository;
import cn.superhuang.data.scalpel.business.service.consumer.credential.repository.GatewayCredentialBindingRepository;
import cn.superhuang.data.scalpel.business.service.consumer.domain.ApiConsumer;
import cn.superhuang.data.scalpel.business.service.consumer.domain.GatewayConsumerBinding;
import cn.superhuang.data.scalpel.business.service.consumer.repository.ApiConsumerRepository;
import cn.superhuang.data.scalpel.business.service.consumer.repository.GatewayConsumerBindingRepository;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.ApiServiceSubscriptionDesiredState;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.GatewaySubscriptionStatus;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionOperationException;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionInspectionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionPort;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionReference;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionResult;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.repository.ApiServiceSubscriptionRepository;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.repository.GatewaySubscriptionBindingRepository;
import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.domain.GatewayServiceBinding;
import cn.superhuang.data.scalpel.business.service.gateway.repository.GatewayServiceBindingRepository;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import com.jayway.jsonpath.JsonPath;
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
@Import(ApiConsumerCredentialSubscriptionIntegrationTests.FakeGatewayConfiguration.class)
class ApiConsumerCredentialSubscriptionIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;
    @Autowired
    private ApiConsumerRepository consumerRepository;
    @Autowired
    private GatewayConsumerBindingRepository consumerBindingRepository;
    @Autowired
    private ApiConsumerCredentialRepository credentialRepository;
    @Autowired
    private GatewayCredentialBindingRepository credentialBindingRepository;
    @Autowired
    private DataServiceRepository dataServiceRepository;
    @Autowired
    private GatewayServiceBindingRepository serviceBindingRepository;
    @Autowired
    private ApiServiceSubscriptionRepository subscriptionRepository;
    @Autowired
    private GatewaySubscriptionBindingRepository subscriptionBindingRepository;
    @Autowired
    private FakeGatewayCredentialPort credentialGateway;
    @Autowired
    private FakeGatewaySubscriptionPort subscriptionGateway;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        subscriptionBindingRepository.deleteAllInBatch();
        subscriptionRepository.deleteAllInBatch();
        credentialBindingRepository.deleteAllInBatch();
        credentialRepository.deleteAllInBatch();
        serviceBindingRepository.deleteAllInBatch();
        consumerBindingRepository.deleteAllInBatch();
        dataServiceRepository.deleteAllInBatch();
        consumerRepository.deleteAllInBatch();
        credentialGateway.reset();
        subscriptionGateway.reset();

        MockMvc unauthenticated = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        String token = loginAsAdministrator(unauthenticated);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .defaultRequest(get("/").header("Authorization", "Bearer " + token))
                .apply(springSecurity())
                .build();
    }

    @Test
    void createsListsRotatesAndDeletesOneTimeApiKeysOutsideTransactions() throws Exception {
        ConsumerFixture consumer = createConsumer();

        String createdBody = mockMvc.perform(post(
                                "/api/v1/api-consumers/{consumerId}/credentials",
                                consumer.id()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"主调用密钥\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.credential.consumerId").value(consumer.id().toString()))
                .andExpect(jsonPath("$.credential.revision").value(1))
                .andExpect(jsonPath("$.credential.gatewayBindings[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.secret").value(org.hamcrest.Matchers.startsWith("dsk_")))
                .andReturn().getResponse().getContentAsString();
        UUID credentialId = UUID.fromString(JsonPath.read(createdBody, "$.credential.id"));
        String firstSecret = JsonPath.read(createdBody, "$.secret");

        mockMvc.perform(get("/api/v1/api-consumers/{consumerId}/credentials", consumer.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(credentialId.toString()))
                .andExpect(jsonPath("$[0].secret").doesNotExist())
                .andExpect(jsonPath("$[0].secretHint").value(org.hamcrest.Matchers.startsWith("dsk_…")));

        String rotatedBody = mockMvc.perform(post(
                                "/api/v1/api-consumers/{consumerId}/credentials/{credentialId}/actions/rotate",
                                consumer.id(),
                                credentialId
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credential.revision").value(2))
                .andExpect(jsonPath("$.credential.gatewayBindings[0].syncedRevision").value(2))
                .andExpect(jsonPath("$.secret").value(org.hamcrest.Matchers.startsWith("dsk_")))
                .andReturn().getResponse().getContentAsString();
        String rotatedSecret = JsonPath.read(rotatedBody, "$.secret");

        assertThat(rotatedSecret).isNotEqualTo(firstSecret);
        assertThat(credentialGateway.upserts).hasSize(2);
        assertThat(credentialGateway.upsertTransactionStates).containsOnly(false);

        mockMvc.perform(post(
                                "/api/v1/api-consumers/{consumerId}/credentials/{credentialId}/actions/delete",
                                consumer.id(),
                                credentialId
                        ))
                .andExpect(status().isNoContent());

        assertThat(credentialRepository.findById(credentialId)).isEmpty();
        assertThat(credentialGateway.removeTransactionStates).containsOnly(false);
    }

    @Test
    void preservesCredentialFailuresWithoutReturningSecretsAndSupportsRecovery() throws Exception {
        ConsumerFixture consumer = createConsumer();
        credentialGateway.failUpsert = true;

        String failedBody = mockMvc.perform(post(
                                "/api/v1/api-consumers/{consumerId}/credentials",
                                consumer.id()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"失败密钥\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.credential.gatewayBindings[0].status").value("SYNC_FAILED"))
                .andExpect(jsonPath("$.credential.gatewayBindings[0].lastError").value("模拟 API Key 同步失败"))
                .andExpect(jsonPath("$.secret").isEmpty())
                .andReturn().getResponse().getContentAsString();
        UUID credentialId = UUID.fromString(JsonPath.read(failedBody, "$.credential.id"));

        credentialGateway.failUpsert = false;
        mockMvc.perform(post(
                                "/api/v1/api-consumers/{consumerId}/credentials/{credentialId}/actions/rotate",
                                consumer.id(),
                                credentialId
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credential.gatewayBindings[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.secret").value(org.hamcrest.Matchers.startsWith("dsk_")));

        credentialGateway.failRemove = true;
        mockMvc.perform(post(
                                "/api/v1/api-consumers/{consumerId}/credentials/{credentialId}/actions/delete",
                                consumer.id(),
                                credentialId
                        ))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "网关 API Key 删除失败：DATASCALPEL：模拟 API Key 删除失败"
                ));
        assertThat(credentialBindingRepository.findAllByCredentialId(credentialId))
                .singleElement()
                .extracting(binding -> binding.getStatus())
                .isEqualTo(GatewayCredentialStatus.DELETE_FAILED);

        credentialGateway.failRemove = false;
        mockMvc.perform(post(
                                "/api/v1/api-consumers/{consumerId}/credentials/{credentialId}/actions/delete",
                                consumer.id(),
                                credentialId
                        ))
                .andExpect(status().isNoContent());
    }

    @Test
    void reconcilesCredentialReadOnlyAndRequiresExplicitRotationForRecovery() throws Exception {
        ConsumerFixture consumer = createConsumer();
        String createdBody = mockMvc.perform(post(
                                "/api/v1/api-consumers/{consumerId}/credentials",
                                consumer.id()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"待对账密钥\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID credentialId = UUID.fromString(JsonPath.read(createdBody, "$.credential.id"));
        credentialGateway.inspectResult = GatewayInspectionResult.drifted(
                GatewayReconciliationReason.REMOTE_MISSING,
                "模拟远端 API Key 丢失"
        );

        mockMvc.perform(post(
                                "/api/v1/api-consumers/{consumerId}/credentials/{credentialId}/actions/reconcile-gateway",
                                consumer.id(),
                                credentialId
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayBindings[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.gatewayBindings[0].reconciliationStatus").value("DRIFTED"))
                .andExpect(jsonPath("$.gatewayBindings[0].reconciliationReason").value("REMOTE_MISSING"));

        assertThat(credentialGateway.inspectTransactionStates).containsOnly(false);
        assertThat(credentialGateway.upserts).hasSize(1);

        mockMvc.perform(post(
                                "/api/v1/api-consumers/{consumerId}/credentials/{credentialId}/actions/rotate",
                                consumer.id(),
                                credentialId
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credential.gatewayBindings[0].reconciliationStatus")
                        .value("NOT_CHECKED"))
                .andExpect(jsonPath("$.secret").value(org.hamcrest.Matchers.startsWith("dsk_")));
        assertThat(credentialGateway.upserts).hasSize(2);
    }

    @Test
    void grantsFiltersAndRevokesSubscriptionsWithImmutableDesiredState() throws Exception {
        ConsumerFixture consumer = createConsumer();
        ServiceFixture service = createService(DataServiceAccessMode.SUBSCRIPTION_REQUIRED, true);

        String createdBody = createSubscription(consumer.id(), service.id())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.desiredState").value("GRANTED"))
                .andExpect(jsonPath("$.gatewayBindings[0].status").value("GRANTED"))
                .andReturn().getResponse().getContentAsString();
        UUID subscriptionId = UUID.fromString(JsonPath.read(createdBody, "$.id"));

        mockMvc.perform(get("/api/v1/api-service-subscriptions")
                        .param("consumerId", consumer.id().toString())
                        .param("dataServiceId", service.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(subscriptionId.toString()));
        mockMvc.perform(get("/api/v1/api-service-subscriptions")
                        .param("consumerId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        createSubscription(consumer.id(), service.id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("消费者已经订阅该数据服务"));

        assertThat(subscriptionGateway.grants).singleElement().satisfies(spec -> {
            assertThat(spec.consumerExternalId()).isEqualTo(consumer.externalId());
            assertThat(spec.serviceExternalId()).isEqualTo(service.externalServiceId());
            assertThat(spec.routeExternalId()).isEqualTo(service.externalRouteId());
        });
        assertThat(subscriptionGateway.grantTransactionStates).containsOnly(false);

        mockMvc.perform(post(
                                "/api/v1/api-service-subscriptions/{id}/actions/revoke",
                                subscriptionId
                        ))
                .andExpect(status().isNoContent());
        assertThat(subscriptionRepository.findById(subscriptionId)).isEmpty();
        assertThat(subscriptionGateway.revokeTransactionStates).containsOnly(false);
    }

    @Test
    void reconcilesSubscriptionReadOnlyAndLeavesRepairToExplicitSync() throws Exception {
        ConsumerFixture consumer = createConsumer();
        ServiceFixture service = createService(DataServiceAccessMode.SUBSCRIPTION_REQUIRED, true);
        String createdBody = createSubscription(consumer.id(), service.id())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID subscriptionId = UUID.fromString(JsonPath.read(createdBody, "$.id"));
        subscriptionGateway.inspectResult = GatewayInspectionResult.drifted(
                GatewayReconciliationReason.CONFIG_MISMATCH,
                "模拟 ACL 组不一致"
        );

        mockMvc.perform(post(
                                "/api/v1/api-service-subscriptions/{id}/actions/reconcile-gateway",
                                subscriptionId
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayBindings[0].status").value("GRANTED"))
                .andExpect(jsonPath("$.gatewayBindings[0].reconciliationStatus").value("DRIFTED"))
                .andExpect(jsonPath("$.gatewayBindings[0].reconciliationReason").value("CONFIG_MISMATCH"));

        assertThat(subscriptionGateway.inspectTransactionStates).containsOnly(false);
        assertThat(subscriptionGateway.grants).hasSize(1);

        mockMvc.perform(post("/api/v1/api-service-subscriptions/{id}/actions/sync", subscriptionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayBindings[0].reconciliationStatus").value("NOT_CHECKED"));
        assertThat(subscriptionGateway.grants).hasSize(2);
    }

    @Test
    void preservesGrantAndRevokeFailuresAndPreventsStaleRegrant() throws Exception {
        ConsumerFixture consumer = createConsumer();
        ServiceFixture service = createService(DataServiceAccessMode.SUBSCRIPTION_REQUIRED, true);
        subscriptionGateway.failGrant = true;

        String failedBody = createSubscription(consumer.id(), service.id())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.desiredState").value("GRANTED"))
                .andExpect(jsonPath("$.gatewayBindings[0].status").value("GRANT_FAILED"))
                .andExpect(jsonPath("$.gatewayBindings[0].lastError").value("模拟订阅授权失败"))
                .andReturn().getResponse().getContentAsString();
        UUID subscriptionId = UUID.fromString(JsonPath.read(failedBody, "$.id"));

        subscriptionGateway.failGrant = false;
        mockMvc.perform(post("/api/v1/api-service-subscriptions/{id}/actions/sync", subscriptionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayBindings[0].status").value("GRANTED"));

        subscriptionGateway.failRevoke = true;
        mockMvc.perform(post(
                                "/api/v1/api-service-subscriptions/{id}/actions/revoke",
                                subscriptionId
                        ))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "网关订阅撤回失败：DATASCALPEL：模拟订阅撤回失败"
                ));

        assertThat(subscriptionRepository.findById(subscriptionId))
                .get()
                .extracting(subscription -> subscription.getDesiredState())
                .isEqualTo(ApiServiceSubscriptionDesiredState.REVOKED);
        assertThat(subscriptionBindingRepository.findAllBySubscriptionId(subscriptionId))
                .singleElement()
                .extracting(binding -> binding.getStatus())
                .isEqualTo(GatewaySubscriptionStatus.REVOKE_FAILED);

        mockMvc.perform(post("/api/v1/api-service-subscriptions/{id}/actions/sync", subscriptionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("订阅正在撤回，不能重新授权"));

        subscriptionGateway.failRevoke = false;
        mockMvc.perform(post(
                                "/api/v1/api-service-subscriptions/{id}/actions/revoke",
                                subscriptionId
                        ))
                .andExpect(status().isNoContent());
        assertThat(subscriptionRepository.findById(subscriptionId)).isEmpty();
    }

    @Test
    void validatesSubscriptionPrerequisitesAndProtectsReferencedResources() throws Exception {
        ConsumerFixture consumer = createConsumer();
        ServiceFixture publicService = createService(DataServiceAccessMode.PUBLIC, true);
        createSubscription(consumer.id(), publicService.id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("公开数据服务不需要订阅"));

        ServiceFixture unpublished = createService(DataServiceAccessMode.SUBSCRIPTION_REQUIRED, false);
        createSubscription(consumer.id(), unpublished.id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("数据服务尚未发布到当前网关"));

        ServiceFixture protectedService = createService(DataServiceAccessMode.SUBSCRIPTION_REQUIRED, true);
        String subscriptionBody = createSubscription(consumer.id(), protectedService.id())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID subscriptionId = UUID.fromString(JsonPath.read(subscriptionBody, "$.id"));

        mockMvc.perform(post("/api/v1/api-consumers/{id}/actions/delete", consumer.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("消费者仍有数据服务订阅，请先撤回订阅"));

        DataService entity = dataServiceRepository.findById(protectedService.id()).orElseThrow();
        entity.markDisabled();
        dataServiceRepository.saveAndFlush(entity);
        serviceBindingRepository.deleteById(protectedService.bindingId());
        serviceBindingRepository.flush();

        mockMvc.perform(post("/api/v1/data-services/{id}/actions/delete", protectedService.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("数据服务仍有消费者订阅，请先撤回订阅"));

        mockMvc.perform(post(
                                "/api/v1/api-service-subscriptions/{id}/actions/revoke",
                                subscriptionId
                        ))
                .andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.ResultActions createSubscription(
            UUID consumerId,
            UUID dataServiceId
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/api-service-subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"consumerId":"%s","dataServiceId":"%s"}
                        """.formatted(consumerId, dataServiceId)));
    }

    private ConsumerFixture createConsumer() {
        String suffix = suffix();
        ApiConsumer consumer = consumerRepository.saveAndFlush(
                ApiConsumer.create("consumer-" + suffix, "测试消费者", null)
        );
        GatewayConsumerBinding binding = GatewayConsumerBinding.pending(
                consumer.getId(),
                GatewayProvider.DATASCALPEL
        );
        binding.beginSync();
        String externalId = "consumer-external-" + consumer.getId();
        binding.synchronizedWith(externalId, consumer.getRevision());
        consumerBindingRepository.saveAndFlush(binding);
        return new ConsumerFixture(consumer.getId(), externalId);
    }

    private ServiceFixture createService(DataServiceAccessMode accessMode, boolean published) {
        String suffix = suffix();
        DataService service = DataService.create(
                "service_" + suffix,
                "测试数据服务",
                null,
                DataServiceType.STANDARD_TABLE,
                UUID.randomUUID(),
                "/open-api/v1/subscription-" + suffix,
                accessMode,
                null
        );
        service.nextRevision();
        service.markEnabled();
        dataServiceRepository.saveAndFlush(service);

        if (!published) {
            return new ServiceFixture(service.getId(), null, null, null);
        }
        GatewayServiceBinding binding = GatewayServiceBinding.publishing(
                service.getId(),
                GatewayProvider.DATASCALPEL
        );
        binding.beginPublish();
        String externalServiceId = "service-external-" + service.getId();
        String externalRouteId = "route-external-" + service.getId();
        binding.publishedWith(
                externalServiceId,
                externalRouteId,
                "http://gateway.test" + service.getRoutePath(),
                service.getRevision()
        );
        serviceBindingRepository.saveAndFlush(binding);
        return new ServiceFixture(
                service.getId(),
                binding.getId(),
                externalServiceId,
                externalRouteId
        );
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private record ConsumerFixture(UUID id, String externalId) {
    }

    private record ServiceFixture(
            UUID id,
            UUID bindingId,
            String externalServiceId,
            String externalRouteId
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeGatewayConfiguration {

        @Bean
        @Primary
        FakeGatewayCredentialPort fakeGatewayCredentialPort() {
            return new FakeGatewayCredentialPort();
        }

        @Bean
        @Primary
        FakeGatewaySubscriptionPort fakeGatewaySubscriptionPort() {
            return new FakeGatewaySubscriptionPort();
        }
    }

    static class FakeGatewayCredentialPort implements GatewayCredentialPort {

        private final List<GatewayCredentialSpec> upserts = new ArrayList<>();
        private final List<Boolean> upsertTransactionStates = new ArrayList<>();
        private final List<Boolean> removeTransactionStates = new ArrayList<>();
        private final List<Boolean> inspectTransactionStates = new ArrayList<>();
        private boolean failUpsert;
        private boolean failRemove;
        private GatewayInspectionResult inspectResult =
                GatewayInspectionResult.inSync("模拟 API Key 状态一致");

        @Override
        public GatewayProvider provider() {
            return GatewayProvider.DATASCALPEL;
        }

        @Override
        public GatewayCredentialResult upsert(GatewayCredentialSpec credential) {
            upserts.add(credential);
            upsertTransactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (failUpsert) throw new GatewayCredentialOperationException("模拟 API Key 同步失败");
            return new GatewayCredentialResult("credential-external-" + credential.id());
        }

        @Override
        public void remove(GatewayCredentialReference credential) {
            removeTransactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (failRemove) throw new GatewayCredentialOperationException("模拟 API Key 删除失败");
        }

        @Override
        public GatewayInspectionResult inspect(GatewayCredentialInspectionSpec credential) {
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
            inspectResult = GatewayInspectionResult.inSync("模拟 API Key 状态一致");
        }
    }

    static class FakeGatewaySubscriptionPort implements GatewaySubscriptionPort {

        private final List<GatewaySubscriptionSpec> grants = new ArrayList<>();
        private final List<Boolean> grantTransactionStates = new ArrayList<>();
        private final List<Boolean> revokeTransactionStates = new ArrayList<>();
        private final List<Boolean> inspectTransactionStates = new ArrayList<>();
        private boolean failGrant;
        private boolean failRevoke;
        private GatewayInspectionResult inspectResult =
                GatewayInspectionResult.inSync("模拟订阅状态一致");

        @Override
        public GatewayProvider provider() {
            return GatewayProvider.DATASCALPEL;
        }

        @Override
        public GatewaySubscriptionResult grant(GatewaySubscriptionSpec subscription) {
            grants.add(subscription);
            grantTransactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (failGrant) throw new GatewaySubscriptionOperationException("模拟订阅授权失败");
            return new GatewaySubscriptionResult("membership-external-" + subscription.id());
        }

        @Override
        public void revoke(GatewaySubscriptionReference subscription) {
            revokeTransactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (failRevoke) throw new GatewaySubscriptionOperationException("模拟订阅撤回失败");
        }

        @Override
        public GatewayInspectionResult inspect(GatewaySubscriptionInspectionSpec subscription) {
            inspectTransactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            return inspectResult;
        }

        void reset() {
            grants.clear();
            grantTransactionStates.clear();
            revokeTransactionStates.clear();
            inspectTransactionStates.clear();
            failGrant = false;
            failRevoke = false;
            inspectResult = GatewayInspectionResult.inSync("模拟订阅状态一致");
        }
    }
}
