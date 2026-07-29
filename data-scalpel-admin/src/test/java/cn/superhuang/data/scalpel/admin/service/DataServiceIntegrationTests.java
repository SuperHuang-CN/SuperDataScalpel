package cn.superhuang.data.scalpel.admin.service;

import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.service.ServiceEngineClient;
import cn.superhuang.data.scalpel.business.service.ServiceEngineCredentialCipher;
import cn.superhuang.data.scalpel.business.service.consumer.domain.ApiConsumer;
import cn.superhuang.data.scalpel.business.service.consumer.repository.ApiConsumerRepository;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.ApiServiceSubscription;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.repository.ApiServiceSubscriptionRepository;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Import(DataServiceIntegrationTests.EngineClientConfiguration.class)
class DataServiceIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;
    @Autowired
    private DataServiceRepository dataServiceRepository;
    @Autowired
    private ApiConsumerRepository consumerRepository;
    @Autowired
    private ApiServiceSubscriptionRepository subscriptionRepository;
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
    private PlatformTransactionManager transactionManager;

    private final List<UUID> serviceIds = new ArrayList<>();
    private final List<UUID> consumerIds = new ArrayList<>();
    private final List<UUID> registrationIds = new ArrayList<>();
    private final List<UUID> engineIds = new ArrayList<>();
    private final List<UUID> dataSourceIds = new ArrayList<>();
    private final List<UUID> modelIds = new ArrayList<>();
    private MockMvc mockMvc;

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
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            serviceIds.forEach(serviceId ->
                    subscriptionRepository.findAllByDataServiceId(serviceId)
                            .forEach(subscriptionRepository::delete)
            );
            serviceIds.forEach(serviceId -> {
                sqlModelReferenceRepository.deleteAllByDataServiceId(serviceId);
                sqlParameterRepository.deleteAllByDataServiceId(serviceId);
                sqlDefinitionRepository.deleteByDataServiceId(serviceId);
                standardDefinitionRepository.deleteByDataServiceId(serviceId);
                if (dataServiceRepository.existsById(serviceId)) dataServiceRepository.deleteById(serviceId);
            });
            consumerIds.forEach(consumerId -> {
                if (consumerRepository.existsById(consumerId)) consumerRepository.deleteById(consumerId);
            });
            modelIds.forEach(modelId -> {
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
    void rejectsChangingProtectedServiceToPublicWhileSubscriptionsExist() throws Exception {
        String suffix = suffix();
        UUID dataSourceId = createDataSource("protected_" + suffix, "POSTGRESQL");
        UUID modelId = createModel(dataSourceId, "protected_" + suffix, DataModelStatus.DRAFT);
        UUID engineId = createEngine("protected_engine_" + suffix);
        register(engineId, dataSourceId);

        String createRequest = sqlServiceRequest(
                "protected_" + suffix,
                engineId,
                dataSourceId,
                "/open-api/v1/protected-" + suffix,
                List.of(modelId)
        ).replace(
                "\"type\":\"SQL_QUERY\"",
                "\"accessMode\":\"SUBSCRIPTION_REQUIRED\",\"type\":\"SQL_QUERY\""
        );
        String createResponse = mockMvc.perform(post("/api/v1/data-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessMode").value("SUBSCRIPTION_REQUIRED"))
                .andReturn().getResponse().getContentAsString();
        UUID serviceId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(createResponse, "$.id"));
        serviceIds.add(serviceId);

        ApiConsumer consumer = consumerRepository.saveAndFlush(
                ApiConsumer.create("protected-" + suffix, "访问模式测试消费者", null)
        );
        consumerIds.add(consumer.getId());
        subscriptionRepository.saveAndFlush(
                ApiServiceSubscription.create(consumer.getId(), serviceId)
        );

        mockMvc.perform(post("/api/v1/data-services/{id}/actions/update", serviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"受保护服务",
                                  "engineId":"%s",
                                  "routePath":"/open-api/v1/protected-%s",
                                  "accessMode":"PUBLIC",
                                  "type":"SQL_QUERY",
                                  "standardDefinition":null,
                                  "sqlDefinition":{
                                    "dataSourceId":"%s",
                                    "modelIds":["%s"],
                                    "sqlText":"select id, name from customer where department_id = :departmentId",
                                    "parameters":[{
                                      "name":"departmentId",
                                      "typeDefinition":{"type":"LONG"},
                                      "required":true,
                                      "description":"部门 ID"
                                    }]
                                  }
                                }
                                """.formatted(engineId, suffix, dataSourceId, modelId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "数据服务仍有消费者订阅，请先撤回订阅后再改为公开访问"
                ));
    }

    @Test
    void managesSqlDefinitionWithoutReturningSqlInListAndRejectsTypeChanges() throws Exception {
        String suffix = suffix();
        UUID dataSourceId = createDataSource("query_" + suffix, "POSTGRESQL");
        UUID draftModelId = createModel(dataSourceId, "draft_" + suffix, DataModelStatus.DRAFT);
        UUID publishedModelId = createModel(dataSourceId, "published_" + suffix, DataModelStatus.PUBLISHED);
        UUID disabledModelId = createModel(dataSourceId, "disabled_" + suffix, DataModelStatus.DISABLED);
        UUID engineId = createEngine("query_engine_" + suffix);
        register(engineId, dataSourceId);

        String createResponse = mockMvc.perform(post("/api/v1/data-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sqlServiceRequest(
                                "customer_" + suffix, engineId, dataSourceId,
                                "/open-api/v1/customer-" + suffix,
                                List.of(draftModelId, publishedModelId, disabledModelId)
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SQL_QUERY"))
                .andExpect(jsonPath("$.standardDefinition").isEmpty())
                .andExpect(jsonPath("$.sqlDefinition.dataSourceId").value(dataSourceId.toString()))
                .andExpect(jsonPath("$.sqlDefinition.modelIds[0]").value(draftModelId.toString()))
                .andExpect(jsonPath("$.sqlDefinition.modelIds[1]").value(publishedModelId.toString()))
                .andExpect(jsonPath("$.sqlDefinition.modelIds[2]").value(disabledModelId.toString()))
                .andExpect(jsonPath("$.sqlDefinition.parameters[0].name").value("departmentId"))
                .andReturn().getResponse().getContentAsString();
        UUID serviceId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(createResponse, "$.id"));
        serviceIds.add(serviceId);

        mockMvc.perform(get("/api/v1/data-services/{id}", serviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sqlDefinition.sqlText").value(
                        "select id, name from customer where department_id = :departmentId"
                ));

        mockMvc.perform(get("/api/v1/data-services")
                        .param("search", "code:\"customer_" + suffix + "\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("SQL_QUERY"))
                .andExpect(jsonPath("$.content[0].sourceId").value(dataSourceId.toString()))
                .andExpect(jsonPath("$.content[0].sourceName").value("查询数据源"))
                .andExpect(jsonPath("$.content[0].sqlDefinition").doesNotExist())
                .andExpect(jsonPath("$.content[0].sqlText").doesNotExist());

        mockMvc.perform(post("/api/v1/data-services/{id}/actions/update", serviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"客户 SQL 查询",
                                  "engineId":"%s",
                                  "routePath":"/open-api/v1/customer-%s",
                                  "type":"SQL_QUERY",
                                  "standardDefinition":null,
                                  "sqlDefinition":{
                                    "dataSourceId":"%s",
                                    "modelIds":["%s","%s","%s"],
                                    "sqlText":"select id, name from customer where department_id = :departmentId",
                                    "parameters":[{
                                      "name":"departmentId",
                                      "typeDefinition":{"type":"LONG"},
                                      "required":true,
                                      "description":"部门 ID"
                                    }]
                                  }
                                }
                                """.formatted(
                                engineId, suffix, dataSourceId,
                                disabledModelId, draftModelId, publishedModelId
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sqlDefinition.version").value(2))
                .andExpect(jsonPath("$.sqlDefinition.modelIds[0]").value(disabledModelId.toString()))
                .andExpect(jsonPath("$.sqlDefinition.modelIds[1]").value(draftModelId.toString()))
                .andExpect(jsonPath("$.sqlDefinition.modelIds[2]").value(publishedModelId.toString()));

        mockMvc.perform(post("/api/v1/data-services/{id}/actions/update", serviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"禁止切换类型",
                                  "engineId":"%s",
                                  "routePath":"/open-api/v1/customer-%s",
                                  "type":"STANDARD_TABLE",
                                  "standardDefinition":{"modelId":"%s"},
                                  "sqlDefinition":null
                                }
                                """.formatted(engineId, suffix, UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/delete", draftModelId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("模型已被数据服务使用，不能删除"));

        mockMvc.perform(post("/api/v1/data-services/{id}/actions/delete", serviceId))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(
                sqlModelReferenceRepository.findAllByDataServiceIdOrderBySortOrderAsc(serviceId)
        ).isEmpty();
        mockMvc.perform(post("/api/v1/models/{id}/actions/delete", draftModelId))
                .andExpect(status().isNoContent());
    }

    @Test
    void validatesMutualDefinitionsCapabilityAndReadOnlySqlTestProblems() throws Exception {
        String suffix = suffix();
        UUID postgresId = createDataSource("postgres_" + suffix, "POSTGRESQL");
        UUID mysqlId = createDataSource("mysql_" + suffix, "MYSQL");
        UUID postgresModelId = createModel(postgresId, "postgres_" + suffix, DataModelStatus.DRAFT);
        UUID mysqlModelId = createModel(mysqlId, "mysql_" + suffix, DataModelStatus.DRAFT);
        UUID engineId = createEngine("validation_engine_" + suffix);
        register(engineId, postgresId);

        mockMvc.perform(post("/api/v1/data-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"invalid_shape_%s",
                                  "name":"互斥定义",
                                  "engineId":"%s",
                                  "routePath":"/open-api/v1/invalid-shape-%s",
                                  "type":"SQL_QUERY",
                                  "standardDefinition":{"modelId":"%s"},
                                  "sqlDefinition":{"dataSourceId":"%s","modelIds":["%s"],"sqlText":"select 1","parameters":[]}
                                }
                                """.formatted(suffix, engineId, suffix, UUID.randomUUID(), postgresId, postgresModelId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(post("/api/v1/data-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sqlServiceRequest(
                                "mysql_query_" + suffix, engineId, mysqlId,
                                "/open-api/v1/mysql-query-" + suffix,
                                List.of(mysqlModelId)
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("当前数据库类型未开放 SQL 服务能力"));

        mockMvc.perform(post("/api/v1/data-services/actions/test-sql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dataSourceId":"%s",
                                  "modelIds":["%s"],
                                  "sqlText":"delete from customer",
                                  "parameters":[],
                                  "arguments":{},
                                  "previewSize":20
                                }
                                """.formatted(postgresId, postgresModelId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.problems[0].code").value("SQL_NOT_READ_ONLY"));
    }

    @Test
    void rejectsInvalidModelSelectionsButDoesNotValidateSqlTableAgainstSelectedModels() throws Exception {
        String suffix = suffix();
        UUID selectedDataSourceId = createDataSource("selected_" + suffix, "POSTGRESQL");
        UUID otherDataSourceId = createDataSource("other_" + suffix, "POSTGRESQL");
        UUID selectedModelId = createModel(selectedDataSourceId, "selected_" + suffix, DataModelStatus.DRAFT);
        UUID otherModelId = createModel(otherDataSourceId, "other_" + suffix, DataModelStatus.DRAFT);
        UUID engineId = createEngine("model_validation_" + suffix);
        register(engineId, selectedDataSourceId);

        mockMvc.perform(post("/api/v1/data-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sqlServiceRequest(
                                "empty_models_" + suffix, engineId, selectedDataSourceId,
                                "/open-api/v1/empty-models-" + suffix, List.of()
                        )))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/data-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sqlServiceRequest(
                                "duplicate_models_" + suffix, engineId, selectedDataSourceId,
                                "/open-api/v1/duplicate-models-" + suffix,
                                List.of(selectedModelId, selectedModelId)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("SQL 服务关联模型不能为空或重复"));

        mockMvc.perform(post("/api/v1/data-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sqlServiceRequest(
                                "missing_model_" + suffix, engineId, selectedDataSourceId,
                                "/open-api/v1/missing-model-" + suffix, List.of(UUID.randomUUID())
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("SQL 服务关联模型不存在"));

        mockMvc.perform(post("/api/v1/data-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sqlServiceRequest(
                                "cross_source_" + suffix, engineId, selectedDataSourceId,
                                "/open-api/v1/cross-source-" + suffix, List.of(otherModelId)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("SQL 服务关联模型不属于所选数据源"));

        String response = mockMvc.perform(post("/api/v1/data-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sqlServiceRequest(
                                "unselected_table_" + suffix, engineId, selectedDataSourceId,
                                "/open-api/v1/unselected-table-" + suffix, List.of(selectedModelId),
                                "select * from a_table_not_represented_by_the_selected_model "
                                        + "where department_id = :departmentId"
                        )))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        serviceIds.add(UUID.fromString(com.jayway.jsonpath.JsonPath.read(response, "$.id")));
    }

    private UUID createDataSource(String code, String type) throws Exception {
        String response = mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "name":"查询数据源",
                                  "purposes":["STORAGE"],
                                  "type":"%s",
                                  "enabled":true,
                                  "connection":{
                                    "kind":"JDBC",
                                    "host":"127.0.0.1",
                                    "port":5432,
                                    "databaseName":"query_test",
                                    "schemaName":"public",
                                    "username":"reader",
                                    "password":"secret"
                                  }
                                }
                                """.formatted(code, type)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(com.jayway.jsonpath.JsonPath.read(response, "$.id"));
        dataSourceIds.add(id);
        return id;
    }

    private UUID createEngine(String code) throws Exception {
        String response = mockMvc.perform(post("/api/v1/service-engines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"SQL 测试 Engine",
                                  "adminUrl":"http://engine.test:8081",
                                  "publicUrl":"http://engine.test:8081",
                                  "managementToken":"engine-test-token:%s",
                                  "enabled":true
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(com.jayway.jsonpath.JsonPath.read(response, "$.id"));
        engineIds.add(id);
        return id;
    }

    private UUID createModel(UUID dataSourceId, String code, DataModelStatus status) {
        DataModel model = DataModel.create(
                code, "关联模型 " + code, null, dataSourceId, null, "public", code + "_table",
                PhysicalTableMode.EXTERNAL, null
        );
        if (status == DataModelStatus.PUBLISHED || status == DataModelStatus.DISABLED) {
            model.publish();
        }
        if (status == DataModelStatus.DISABLED) {
            model.disable();
        }
        UUID id = modelRepository.saveAndFlush(model).getId();
        modelIds.add(id);
        return id;
    }

    private void register(UUID engineId, UUID dataSourceId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/service-engine-data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"engineId\":\"" + engineId + "\",\"dataSourceId\":\"" + dataSourceId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andReturn().getResponse().getContentAsString();
        registrationIds.add(UUID.fromString(com.jayway.jsonpath.JsonPath.read(response, "$.id")));
    }

    private static String sqlServiceRequest(
            String code,
            UUID engineId,
            UUID dataSourceId,
            String routePath,
            List<UUID> modelIds
    ) {
        return sqlServiceRequest(
                code, engineId, dataSourceId, routePath, modelIds,
                "select id, name from customer where department_id = :departmentId"
        );
    }

    private static String sqlServiceRequest(
            String code,
            UUID engineId,
            UUID dataSourceId,
            String routePath,
            List<UUID> modelIds,
            String sqlText
    ) {
        String modelIdJson = modelIds.stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(","));
        return """
                {
                  "code":"%s",
                  "name":"客户 SQL 查询",
                  "engineId":"%s",
                  "routePath":"%s",
                  "type":"SQL_QUERY",
                  "standardDefinition":null,
                  "sqlDefinition":{
                    "dataSourceId":"%s",
                    "modelIds":[%s],
                    "sqlText":"%s",
                    "parameters":[{
                      "name":"departmentId",
                      "typeDefinition":{"type":"LONG"},
                      "required":true,
                      "description":"部门 ID"
                    }]
                  }
                }
                """.formatted(code, engineId, routePath, dataSourceId, modelIdJson, sqlText);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EngineClientConfiguration {

        @Bean
        @Primary
        ServiceEngineClient serviceEngineClient(ServiceEngineCredentialCipher credentialCipher) {
            return new ServiceEngineClient(credentialCipher) {
                @Override
                public ServiceEngineInfoResponse info(String adminUrl, String managementToken) {
                    return new ServiceEngineInfoResponse(
                            managementToken.substring("engine-test-token:".length()),
                            List.of("POSTGRESQL", "MYSQL")
                    );
                }

                @Override
                public ServiceEngineInfoResponse info(ServiceEngine engine) {
                    return new ServiceEngineInfoResponse(engine.getCode(), List.of("POSTGRESQL", "MYSQL"));
                }

                @Override
                public EngineDataSourceRegistrationResponse registerDataSource(
                        ServiceEngine engine,
                        EngineDataSourceRegistrationRequest request
                ) {
                    return new EngineDataSourceRegistrationResponse(
                            engine.getCode(), request.dataSourceId(), request.revision(), EngineDataSourceStatus.READY, "已注册"
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
    }
}
