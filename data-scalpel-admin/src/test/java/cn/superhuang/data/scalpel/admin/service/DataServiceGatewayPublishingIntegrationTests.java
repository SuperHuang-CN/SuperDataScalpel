package cn.superhuang.data.scalpel.admin.service;

import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTableInspection;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTablePort;
import cn.superhuang.data.scalpel.business.model.service.PhysicalTableState;
import cn.superhuang.data.scalpel.business.service.ServiceEngineClient;
import cn.superhuang.data.scalpel.business.service.ServiceEngineCredentialCipher;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.repository.GatewayServiceBindingRepository;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceOperationException;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceInspectionSpec;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServicePort;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceReference;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceResult;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceSpec;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceDeploymentRepository;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineDataSourceRegistrationRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineRepository;
import cn.superhuang.data.scalpel.business.service.repository.SqlDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.business.service.repository.SqlDataServiceModelReferenceRepository;
import cn.superhuang.data.scalpel.business.service.repository.SqlDataServiceParameterRepository;
import cn.superhuang.data.scalpel.business.service.repository.StandardDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationResponse;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceStatus;
import cn.superhuang.data.scalpel.contract.service.EngineDeploymentStatus;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceEngineInfoResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceUndeploymentRequest;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.model.DdlPlan;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = "data-scalpel.service-gateway.provider=DATASCALPEL")
@Import(DataServiceGatewayPublishingIntegrationTests.FakeControlPlaneConfiguration.class)
class DataServiceGatewayPublishingIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;
    @Autowired
    private DataServiceRepository serviceRepository;
    @Autowired
    private DataServiceDeploymentRepository deploymentRepository;
    @Autowired
    private GatewayServiceBindingRepository gatewayBindingRepository;
    @Autowired
    private StandardDataServiceDefinitionRepository standardDefinitionRepository;
    @Autowired
    private SqlDataServiceDefinitionRepository sqlDefinitionRepository;
    @Autowired
    private SqlDataServiceModelReferenceRepository sqlModelReferenceRepository;
    @Autowired
    private SqlDataServiceParameterRepository sqlParameterRepository;
    @Autowired
    private ServiceEngineDataSourceRegistrationRepository registrationRepository;
    @Autowired
    private ServiceEngineRepository engineRepository;
    @Autowired
    private DataSourceRepository dataSourceRepository;
    @Autowired
    private DataModelRepository modelRepository;
    @Autowired
    private DataModelFieldRepository fieldRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private FakeGatewayServicePort gateway;
    @Autowired
    private FakeServiceEngineClient engineClient;
    @Autowired
    private OperationRecorder recorder;

    private final List<UUID> serviceIds = new ArrayList<>();
    private final List<UUID> registrationIds = new ArrayList<>();
    private final List<UUID> engineIds = new ArrayList<>();
    private final List<UUID> dataSourceIds = new ArrayList<>();
    private final List<UUID> modelIds = new ArrayList<>();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        gateway.reset();
        engineClient.reset();
        recorder.events.clear();
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
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            serviceIds.forEach(serviceId -> {
                gatewayBindingRepository.findAllByDataServiceId(serviceId)
                        .forEach(gatewayBindingRepository::delete);
                deploymentRepository.findByDataServiceId(serviceId)
                        .ifPresent(deploymentRepository::delete);
                sqlModelReferenceRepository.deleteAllByDataServiceId(serviceId);
                sqlParameterRepository.deleteAllByDataServiceId(serviceId);
                sqlDefinitionRepository.deleteByDataServiceId(serviceId);
                standardDefinitionRepository.deleteByDataServiceId(serviceId);
                if (serviceRepository.existsById(serviceId)) serviceRepository.deleteById(serviceId);
            });
            modelIds.forEach(modelId -> {
                fieldRepository.deleteAllByModelId(modelId);
                if (modelRepository.existsById(modelId)) modelRepository.deleteById(modelId);
            });
            registrationIds.forEach(registrationId -> {
                if (registrationRepository.existsById(registrationId)) registrationRepository.deleteById(registrationId);
            });
            engineIds.forEach(engineId -> {
                if (engineRepository.existsById(engineId)) engineRepository.deleteById(engineId);
            });
            dataSourceIds.forEach(dataSourceId -> {
                if (dataSourceRepository.existsById(dataSourceId)) dataSourceRepository.deleteById(dataSourceId);
            });
        });
    }

    @Test
    void separatesEngineEnablementFromGatewayPublicationAndDisablesInSafeOrder() throws Exception {
        ServiceFixture fixture = createServiceFixture("/open-api/v1/orders-" + suffix());

        mockMvc.perform(post("/api/v1/data-services/{id}/actions/publish", fixture.serviceId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("只有已启用服务可以发布到网关"));

        mockMvc.perform(post("/api/v1/data-services/{id}/actions/enable", fixture.serviceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.deploymentStatus").value("DEPLOYED"))
                .andExpect(jsonPath("$.gatewayBindings").isEmpty());

        mockMvc.perform(post("/api/v1/data-services/{id}/actions/publish", fixture.serviceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.deploymentStatus").value("DEPLOYED"))
                .andExpect(jsonPath("$.gatewayBindings[0].provider").value("DATASCALPEL"))
                .andExpect(jsonPath("$.gatewayBindings[0].publicationStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.gatewayBindings[0].publishedRevision").value(1))
                .andExpect(jsonPath("$.gatewayBindings[0].externalServiceId").value("service-" + fixture.serviceId()))
                .andExpect(jsonPath("$.gatewayBindings[0].externalRouteId").value("route-" + fixture.serviceId()))
                .andExpect(jsonPath("$.gatewayBindings[0].gatewayUrl")
                        .value("http://gateway.test:8000" + fixture.routePath()));

        recorder.events.clear();
        mockMvc.perform(post("/api/v1/data-services/{id}/actions/disable", fixture.serviceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.deploymentStatus").value("REMOVED"))
                .andExpect(jsonPath("$.gatewayBindings").isEmpty());

        assertThat(recorder.events).containsExactly("gateway-remove", "engine-remove");
        assertThat(gateway.transactionStates).containsOnly(false);
        assertThat(engineClient.transactionStates).containsOnly(false);
    }

    @Test
    void unpublishesEveryGatewayBindingWithoutStoppingEngineAndAllowsRepublish() throws Exception {
        ServiceFixture fixture = createServiceFixture("/open-api/v1/unpublish-" + suffix());

        mockMvc.perform(post("/api/v1/data-services/{id}/actions/unpublish", fixture.serviceId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("只有已启用服务可以取消发布"));

        mockMvc.perform(post("/api/v1/data-services/{id}/actions/enable", fixture.serviceId()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/data-services/{id}/actions/publish", fixture.serviceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayBindings[0].publicationStatus").value("PUBLISHED"));

        recorder.events.clear();
        int engineRemovalsBeforeUnpublish = engineClient.removeCount;
        mockMvc.perform(post("/api/v1/data-services/{id}/actions/unpublish", fixture.serviceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.deploymentStatus").value("DEPLOYED"))
                .andExpect(jsonPath("$.gatewayBindings").isEmpty());

        assertThat(recorder.events).containsExactly("gateway-remove");
        assertThat(engineClient.removeCount).isEqualTo(engineRemovalsBeforeUnpublish);

        mockMvc.perform(post("/api/v1/data-services/{id}/actions/unpublish", fixture.serviceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.deploymentStatus").value("DEPLOYED"))
                .andExpect(jsonPath("$.gatewayBindings").isEmpty());
        assertThat(recorder.events).containsExactly("gateway-remove");

        mockMvc.perform(post("/api/v1/data-services/{id}/actions/publish", fixture.serviceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.deploymentStatus").value("DEPLOYED"))
                .andExpect(jsonPath("$.gatewayBindings[0].publicationStatus").value("PUBLISHED"));
        assertThat(recorder.events).containsExactly("gateway-remove", "gateway-publish");
    }

    @Test
    void preservesUnpublishFailureForRetryWithoutStoppingEngine() throws Exception {
        ServiceFixture fixture = createServiceFixture("/open-api/v1/unpublish-retry-" + suffix());
        mockMvc.perform(post("/api/v1/data-services/{id}/actions/enable", fixture.serviceId()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/data-services/{id}/actions/publish", fixture.serviceId()))
                .andExpect(status().isOk());

        int engineRemovalsBeforeFailure = engineClient.removeCount;
        gateway.failRemove = true;
        mockMvc.perform(post("/api/v1/data-services/{id}/actions/unpublish", fixture.serviceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.deploymentStatus").value("DEPLOYED"))
                .andExpect(jsonPath("$.gatewayBindings[0].publicationStatus").value("REMOVE_FAILED"))
                .andExpect(jsonPath("$.gatewayBindings[0].lastError").value("模拟网关撤回失败"));
        assertThat(engineClient.removeCount).isEqualTo(engineRemovalsBeforeFailure);

        gateway.failRemove = false;
        mockMvc.perform(post("/api/v1/data-services/{id}/actions/unpublish", fixture.serviceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.deploymentStatus").value("DEPLOYED"))
                .andExpect(jsonPath("$.gatewayBindings").isEmpty());
        assertThat(engineClient.removeCount).isEqualTo(engineRemovalsBeforeFailure);
    }

    @Test
    void preservesGatewayFailuresForRetryAndDoesNotStopEngineWhenWithdrawalFails() throws Exception {
        ServiceFixture fixture = createServiceFixture("/open-api/v1/retry-" + suffix());
        mockMvc.perform(post("/api/v1/data-services/{id}/actions/enable", fixture.serviceId()))
                .andExpect(status().isOk());

        gateway.failPublish = true;
        mockMvc.perform(post("/api/v1/data-services/{id}/actions/publish", fixture.serviceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.gatewayBindings[0].publicationStatus").value("PUBLISH_FAILED"))
                .andExpect(jsonPath("$.gatewayBindings[0].lastError").value("模拟网关发布失败"));

        gateway.failPublish = false;
        mockMvc.perform(post("/api/v1/data-services/{id}/actions/publish", fixture.serviceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayBindings[0].publicationStatus").value("PUBLISHED"));

        int engineRemovalsBeforeFailure = engineClient.removeCount;
        gateway.failRemove = true;
        mockMvc.perform(post("/api/v1/data-services/{id}/actions/disable", fixture.serviceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.deploymentStatus").value("DEPLOYED"))
                .andExpect(jsonPath("$.gatewayBindings[0].publicationStatus").value("REMOVE_FAILED"))
                .andExpect(jsonPath("$.gatewayBindings[0].lastError").value("模拟网关撤回失败"));
        assertThat(engineClient.removeCount).isEqualTo(engineRemovalsBeforeFailure);

        gateway.failRemove = false;
        mockMvc.perform(post("/api/v1/data-services/{id}/actions/disable", fixture.serviceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.deploymentStatus").value("REMOVED"))
                .andExpect(jsonPath("$.gatewayBindings").isEmpty());
    }

    @Test
    void reconcilesPublishedServiceReadOnlyAndRepairsOnlyAfterExplicitPublish() throws Exception {
        ServiceFixture fixture = createServiceFixture("/open-api/v1/reconcile-" + suffix());
        mockMvc.perform(post("/api/v1/data-services/{id}/actions/enable", fixture.serviceId()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/data-services/{id}/actions/publish", fixture.serviceId()))
                .andExpect(status().isOk());

        gateway.inspectResult = GatewayInspectionResult.drifted(
                GatewayReconciliationReason.CONFIG_MISMATCH,
                "模拟 Kong Route 配置漂移"
        );
        recorder.events.clear();
        mockMvc.perform(post(
                                "/api/v1/data-services/{id}/actions/reconcile-gateway",
                                fixture.serviceId()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayBindings[0].publicationStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.gatewayBindings[0].reconciliationStatus").value("DRIFTED"))
                .andExpect(jsonPath("$.gatewayBindings[0].reconciliationReason").value("CONFIG_MISMATCH"))
                .andExpect(jsonPath("$.gatewayBindings[0].reconciliationMessage")
                        .value("模拟 Kong Route 配置漂移"));

        assertThat(recorder.events).containsExactly("gateway-inspect");

        mockMvc.perform(post("/api/v1/data-services/{id}/actions/publish", fixture.serviceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayBindings[0].publicationStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.gatewayBindings[0].reconciliationStatus").value("NOT_CHECKED"));
        assertThat(recorder.events).containsExactly("gateway-inspect", "gateway-publish");
        assertThat(gateway.transactionStates).containsOnly(false);
    }

    @Test
    void enforcesGatewayRoutePathUniquenessAcrossDifferentEngines() throws Exception {
        String routePath = "/open-api/v1/global-" + suffix();
        createServiceFixture(routePath);

        String suffix = suffix();
        UUID dataSourceId = createDataSource("route_source_" + suffix);
        UUID modelId = createPublishedModel(dataSourceId, "route_model_" + suffix);
        UUID engineId = createEngine("route_engine_" + suffix);
        register(engineId, dataSourceId);

        mockMvc.perform(post("/api/v1/data-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(standardServiceRequest(
                                "duplicate_route_" + suffix,
                                engineId,
                                modelId,
                                routePath
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("数据服务请求路径已被占用"));
    }

    private ServiceFixture createServiceFixture(String routePath) throws Exception {
        String suffix = suffix();
        UUID dataSourceId = createDataSource("gateway_source_" + suffix);
        UUID modelId = createPublishedModel(dataSourceId, "gateway_model_" + suffix);
        UUID engineId = createEngine("gateway_engine_" + suffix);
        register(engineId, dataSourceId);
        String response = mockMvc.perform(post("/api/v1/data-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(standardServiceRequest(
                                "gateway_service_" + suffix,
                                engineId,
                                modelId,
                                routePath
                        )))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID serviceId = UUID.fromString(JsonPath.read(response, "$.id"));
        serviceIds.add(serviceId);
        return new ServiceFixture(serviceId, routePath);
    }

    private UUID createDataSource(String code) throws Exception {
        String response = mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "name":"网关发布数据源",
                                  "purposes":["STORAGE"],
                                  "type":"POSTGRESQL",
                                  "enabled":true,
                                  "connection":{
                                    "kind":"JDBC",
                                    "host":"127.0.0.1",
                                    "port":5432,
                                    "databaseName":"gateway_test",
                                    "schemaName":"public",
                                    "username":"reader",
                                    "password":"secret"
                                  }
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(response, "$.id"));
        dataSourceIds.add(id);
        return id;
    }

    private UUID createPublishedModel(UUID dataSourceId, String code) {
        DataModel model = DataModel.create(
                code,
                "网关发布模型",
                null,
                dataSourceId,
                null,
                "public",
                code + "_table",
                PhysicalTableMode.EXTERNAL,
                null
        );
        model.publish();
        UUID id = modelRepository.saveAndFlush(model).getId();
        fieldRepository.saveAndFlush(DataModelField.create(
                id,
                "id",
                "ID",
                PlatformDataType.LONG,
                null,
                null,
                null,
                false,
                true,
                0,
                null
        ));
        modelIds.add(id);
        return id;
    }

    private UUID createEngine(String code) throws Exception {
        String response = mockMvc.perform(post("/api/v1/service-engines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"网关发布 Engine",
                                  "adminUrl":"http://engine.test:8081",
                                  "publicUrl":"http://engine.test:8081",
                                  "managementToken":"engine-test-token:%s",
                                  "enabled":true
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(response, "$.id"));
        engineIds.add(id);
        return id;
    }

    private void register(UUID engineId, UUID dataSourceId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/service-engine-data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"engineId\":\"" + engineId + "\",\"dataSourceId\":\"" + dataSourceId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andReturn().getResponse().getContentAsString();
        registrationIds.add(UUID.fromString(JsonPath.read(response, "$.id")));
    }

    private static String standardServiceRequest(
            String code,
            UUID engineId,
            UUID modelId,
            String routePath
    ) {
        return """
                {
                  "code":"%s",
                  "name":"网关发布服务",
                  "engineId":"%s",
                  "routePath":"%s",
                  "type":"STANDARD_TABLE",
                  "standardDefinition":{"modelId":"%s"},
                  "sqlDefinition":null
                }
                """.formatted(code, engineId, routePath, modelId);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private record ServiceFixture(UUID serviceId, String routePath) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeControlPlaneConfiguration {

        @Bean
        OperationRecorder operationRecorder() {
            return new OperationRecorder();
        }

        @Bean
        @Primary
        FakeGatewayServicePort fakeGatewayServicePort(OperationRecorder recorder) {
            return new FakeGatewayServicePort(recorder);
        }

        @Bean
        @Primary
        FakeServiceEngineClient fakeServiceEngineClient(
                ServiceEngineCredentialCipher credentialCipher,
                OperationRecorder recorder
        ) {
            return new FakeServiceEngineClient(credentialCipher, recorder);
        }

        @Bean
        @Primary
        ModelPhysicalTablePort fakeModelPhysicalTablePort() {
            return new FakeModelPhysicalTablePort();
        }
    }

    static class FakeModelPhysicalTablePort implements ModelPhysicalTablePort {

        @Override
        public ModelPhysicalTableInspection inspect(
                DataSource dataSource,
                DataModel model,
                List<DataModelField> fields
        ) {
            return matchedInspection();
        }

        @Override
        public ModelPhysicalTableInspection inspect(
                DataSource dataSource,
                DataModel model,
                List<DataModelField> fields,
                TableMetadata metadata
        ) {
            return matchedInspection();
        }

        @Override
        public DdlPlan planCreate(DataSource dataSource, DataModel model, List<DataModelField> fields) {
            throw new UnsupportedOperationException("测试不执行物理建表");
        }

        @Override
        public TableChangePlan planChange(
                DataSource dataSource,
                DataModel model,
                TableDefinition before,
                TableDefinition target
        ) {
            throw new UnsupportedOperationException("测试不执行物理表变更计划");
        }

        @Override
        public void executeChange(
                DataSource dataSource,
                DataModel model,
                TableChangePlan plan,
                TableChangeExecutionMode mode
        ) {
            throw new UnsupportedOperationException("测试不执行物理表变更");
        }

        @Override
        public ModelPhysicalTableInspection create(
                DataSource dataSource,
                DataModel model,
                List<DataModelField> fields
        ) {
            throw new UnsupportedOperationException("测试不执行物理建表");
        }

        private ModelPhysicalTableInspection matchedInspection() {
            return new ModelPhysicalTableInspection(
                    null,
                    PhysicalTableState.MATCHED,
                    true,
                    "matched",
                    List.of()
            );
        }
    }

    static class OperationRecorder {
        private final List<String> events = new ArrayList<>();
    }

    static class FakeGatewayServicePort implements GatewayServicePort {

        private final OperationRecorder recorder;
        private final List<Boolean> transactionStates = new ArrayList<>();
        private boolean failPublish;
        private boolean failRemove;
        private GatewayInspectionResult inspectResult =
                GatewayInspectionResult.inSync("模拟数据服务状态一致");

        FakeGatewayServicePort(OperationRecorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public GatewayProvider provider() {
            return GatewayProvider.DATASCALPEL;
        }

        @Override
        public GatewayServiceResult publish(GatewayServiceSpec service) {
            recorder.events.add("gateway-publish");
            transactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (failPublish) {
                throw new GatewayServiceOperationException("模拟网关发布失败");
            }
            return new GatewayServiceResult(
                    "service-" + service.id(),
                    "route-" + service.id(),
                    "http://gateway.test:8000" + service.routePath()
            );
        }

        @Override
        public void remove(GatewayServiceReference service) {
            recorder.events.add("gateway-remove");
            transactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (failRemove) {
                throw new GatewayServiceOperationException("模拟网关撤回失败");
            }
        }

        @Override
        public GatewayInspectionResult inspect(GatewayServiceInspectionSpec service) {
            recorder.events.add("gateway-inspect");
            transactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            return inspectResult;
        }

        void reset() {
            transactionStates.clear();
            failPublish = false;
            failRemove = false;
            inspectResult = GatewayInspectionResult.inSync("模拟数据服务状态一致");
        }
    }

    static class FakeServiceEngineClient extends ServiceEngineClient {

        private final OperationRecorder recorder;
        private final List<Boolean> transactionStates = new ArrayList<>();
        private int removeCount;

        FakeServiceEngineClient(ServiceEngineCredentialCipher credentialCipher, OperationRecorder recorder) {
            super(credentialCipher);
            this.recorder = recorder;
        }

        @Override
        public ServiceEngineInfoResponse info(String adminUrl, String managementToken) {
            return new ServiceEngineInfoResponse(
                    managementToken.substring("engine-test-token:".length()),
                    List.of("POSTGRESQL")
            );
        }

        @Override
        public ServiceEngineInfoResponse info(ServiceEngine engine) {
            return new ServiceEngineInfoResponse(engine.getCode(), List.of("POSTGRESQL"));
        }

        @Override
        public EngineDataSourceRegistrationResponse registerDataSource(
                ServiceEngine engine,
                EngineDataSourceRegistrationRequest request
        ) {
            return new EngineDataSourceRegistrationResponse(
                    engine.getCode(),
                    request.dataSourceId(),
                    EngineDataSourceStatus.READY,
                    "已注册"
            );
        }

        @Override
        public ServiceDeploymentResponse deploy(ServiceEngine engine, ServiceDeploymentRequest request) {
            recorder.events.add("engine-deploy");
            transactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            return new ServiceDeploymentResponse(
                    request.serviceId(),
                    EngineDeploymentStatus.DEPLOYED,
                    "已启用"
            );
        }

        @Override
        public ServiceDeploymentResponse remove(ServiceEngine engine, ServiceUndeploymentRequest request) {
            recorder.events.add("engine-remove");
            transactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            removeCount++;
            return new ServiceDeploymentResponse(
                    request.serviceId(),
                    EngineDeploymentStatus.REMOVED,
                    "已停用"
            );
        }

        void reset() {
            transactionStates.clear();
            removeCount = 0;
        }
    }
}
