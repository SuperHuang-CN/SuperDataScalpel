package cn.superhuang.data.scalpel.admin.model;

import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.directory.repository.DirectoryRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTableInspection;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTablePort;
import cn.superhuang.data.scalpel.business.model.service.PhysicalTableState;
import cn.superhuang.data.scalpel.dialect.model.DdlPlan;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.model.PrimaryKeyMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableChangeOperation;
import cn.superhuang.data.scalpel.dialect.model.TableChangeOperationType;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionOption;
import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableChangeRisk;
import cn.superhuang.data.scalpel.dialect.model.TableChangeStrategy;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableDdlAtomicity;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.query.StandardQuery;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryResult;
import cn.superhuang.data.scalpel.dialect.model.TableSummary;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Types;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Import(DataModelIntegrationTests.PhysicalTableTestConfiguration.class)
class DataModelIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private DataModelRepository modelRepository;

    @Autowired
    private DataModelFieldRepository fieldRepository;

    @Autowired
    private DataSourceRepository dataSourceRepository;

    @Autowired
    private DirectoryRepository directoryRepository;

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
        clearData();
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    @Test
    void managesFieldsAndLifecycleWhenPhysicalTableIsReady() throws Exception {
        String storageId = createDataSource("model_storage", "模型存储", "STORAGE", true);
        String directoryId = createDirectory("MODEL", "主题模型");

        String created = mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "Order_Fact",
                                  "name": "订单事实模型",
                                  "directoryId": "%s",
                                  "storageDataSourceId": "%s",
                                  "physicalTableName": "Fact_Order",
                                  "description": "第一版只保存元数据"
                                }
                                """.formatted(directoryId, storageId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.model.code").value("order_fact"))
                .andExpect(jsonPath("$.model.catalogName").value("warehouse"))
                .andExpect(jsonPath("$.model.schemaName").value("public"))
                .andExpect(jsonPath("$.model.physicalTableName").value("fact_order"))
                .andExpect(jsonPath("$.model.physicalTableMode").value("MANAGED"))
                .andExpect(jsonPath("$.model.status").value("DRAFT"))
                .andExpect(jsonPath("$.fields.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        String modelId = JsonPath.read(created, "$.model.id");

        mockMvc.perform(get("/api/v1/models")
                        .param("search", "(code:*\"order\"* OR name:*\"订单\"*) AND status:\"DRAFT\"")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].storageDataSourceName").value("模型存储"));

        String fieldsResponse = mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": [
                                    {
                                      "code": "order_id", "name": "订单ID", "fieldType": "LONG",
                                      "nullable": false, "primaryKey": true, "sortOrder": 10
                                    },
                                    {
                                      "code": "amount", "name": "订单金额", "fieldType": "DECIMAL",
                                      "precision": 18, "scale": 2,
                                      "nullable": true, "primaryKey": false, "sortOrder": 20
                                    },
                                    {
                                      "code": "description", "name": "说明", "fieldType": "STRING",
                                      "length": 500, "nullable": true, "primaryKey": false, "sortOrder": 30
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.length()").value(3))
                .andExpect(jsonPath("$.fields[0].primaryKey").value(true))
                .andExpect(jsonPath("$.fields[1].precision").value(18))
                .andExpect(jsonPath("$.fields[2].length").value(500))
                .andReturn().getResponse().getContentAsString();
        String orderIdFieldId = JsonPath.read(fieldsResponse, "$.fields[0].id");

        mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": [
                                    {
                                      "id": "%s", "code": "order_id", "name": "订单主键", "fieldType": "LONG",
                                      "nullable": false, "primaryKey": true, "sortOrder": 10
                                    }
                                  ]
                                }
                                """.formatted(orderIdFieldId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.length()").value(1))
                .andExpect(jsonPath("$.fields[0].id").value(orderIdFieldId))
                .andExpect(jsonPath("$.fields[0].name").value("订单主键"));

        mockMvc.perform(get("/api/v1/models/{id}/physical-table", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_FOUND"));

        mockMvc.perform(get("/api/v1/models/{id}/physical-table/ddl", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supported").value(true));

        mockMvc.perform(post("/api/v1/models/{id}/actions/create-physical-table", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MATCHED"));

        mockMvc.perform(get("/api/v1/models/{id}/data-preview", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(50))
                .andExpect(jsonPath("$.rows[0].order_id").value("1001"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/query-data", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageNo": 1, "pageSize": 20,
                                  "filters": [{"field": "order_id", "operator": "GT", "value": 1000}],
                                  "orders": [{"field": "order_id", "direction": "DESC"}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNo").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.rows[0].order_id").value("1001"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("受管物理表已存在，请先生成并执行物理表变更计划"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/publish", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model.status").value("PUBLISHED"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("只有草稿模型可以修改字段结构"));

        mockMvc.perform(post("/api/v1/data-sources/{id}/actions/delete", storageId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("数据源已被模型使用，不能删除"));

        mockMvc.perform(get("/api/v1/directories").param("scope", "MODEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].directResourceCount").value(1))
                .andExpect(jsonPath("$[0].resourceCount").value(1));

        mockMvc.perform(post("/api/v1/directories/{id}/actions/delete", directoryId))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/models/{id}/actions/disable", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model.status").value("DISABLED"));
        mockMvc.perform(post("/api/v1/models/{id}/actions/enable", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model.status").value("PUBLISHED"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/delete", modelId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("已发布模型请先停用后再删除"));
        mockMvc.perform(post("/api/v1/models/{id}/actions/disable", modelId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/models/{id}/actions/delete", modelId))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/data-sources/{id}/actions/delete", storageId))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/directories/{id}/actions/delete", directoryId))
                .andExpect(status().isNoContent());
    }

    @Test
    void importsExistingTableFieldsAndOnlyAllowsBusinessFieldChanges() throws Exception {
        String storageId = createDataSource("external_storage", "外部表存储", "STORAGE", true);

        mockMvc.perform(get("/api/v1/models/external-table-import-preview")
                        .param("storageDataSourceId", storageId)
                        .param("physicalTableName", "external_orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importable").value(true))
                .andExpect(jsonPath("$.columns.length()").value(3))
                .andExpect(jsonPath("$.columns[0].platformType").value("LONG"))
                .andExpect(jsonPath("$.columns[1].platformType").value("DECIMAL"))
                .andExpect(jsonPath("$.columns[2].platformType").value("STRING"))
                .andExpect(jsonPath("$.columns[2].length").value(200));

        String created = mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "external_order", "name": "外部订单模型",
                                  "storageDataSourceId": "%s", "physicalTableName": "external_orders",
                                  "physicalTableMode": "EXTERNAL"
                                }
                                """.formatted(storageId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.model.physicalTableMode").value("EXTERNAL"))
                .andExpect(jsonPath("$.fields.length()").value(3))
                .andExpect(jsonPath("$.fields[0].code").value("order_id"))
                .andExpect(jsonPath("$.fields[0].fieldType").value("LONG"))
                .andExpect(jsonPath("$.fields[0].primaryKey").value(true))
                .andExpect(jsonPath("$.fields[1].fieldType").value("DECIMAL"))
                .andExpect(jsonPath("$.fields[2].fieldType").value("STRING"))
                .andReturn().getResponse().getContentAsString();
        String modelId = JsonPath.read(created, "$.model.id");
        String orderId = JsonPath.read(created, "$.fields[0].id");
        String amountId = JsonPath.read(created, "$.fields[1].id");
        String remarkId = JsonPath.read(created, "$.fields[2].id");

        mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields": [{
                                  "id": "%s", "code": "order_id", "name": "订单ID", "fieldType": "INTEGER",
                                  "nullable": false, "primaryKey": true, "sortOrder": 10
                                }]}
                                """.formatted(orderId)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields": [
                                  {
                                    "id": "%s", "code": "order_id", "name": "订单主键", "fieldType": "LONG",
                                    "nullable": false, "primaryKey": true, "sortOrder": 30, "description": "来自外部订单表"
                                  },
                                  {
                                    "id": "%s", "code": "amount", "name": "订单金额", "fieldType": "DECIMAL",
                                    "precision": 18, "scale": 2, "nullable": true, "primaryKey": false, "sortOrder": 10
                                  },
                                  {
                                    "id": "%s", "code": "remark", "name": "备注", "fieldType": "STRING",
                                    "length": 200, "nullable": true, "primaryKey": false, "sortOrder": 20
                                  }
                                ]}
                                """.formatted(orderId, amountId, remarkId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[2].name").value("订单主键"))
                .andExpect(jsonPath("$.fields[2].description").value("来自外部订单表"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/update", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "重新绑定后的外部订单模型",
                                  "storageDataSourceId": "%s", "physicalTableName": "external_orders_v2",
                                  "physicalTableMode": "EXTERNAL"
                                }
                                """.formatted(storageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model.physicalTableName").value("external_orders_v2"))
                .andExpect(jsonPath("$.model.schemaVersion").value(3))
                .andExpect(jsonPath("$.fields.length()").value(3))
                .andExpect(jsonPath("$.fields[0].description").value("订单主键"));
    }

    @Test
    void rejectsExistingTableWithUnsupportedColumnType() throws Exception {
        String storageId = createDataSource("unsupported_storage", "不支持类型存储", "STORAGE", true);

        mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "unsupported_external", "name": "不支持类型模型",
                                  "storageDataSourceId": "%s", "physicalTableName": "unsupported_external",
                                  "physicalTableMode": "EXTERNAL"
                                }
                                """.formatted(storageId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatesStoragePurposePhysicalIdentityAndFieldDefinitions() throws Exception {
        String sourceId = createDataSource("source_only", "普通数据源", "SOURCE", true);
        String storageId = createDataSource("storage_only", "数据存储", "STORAGE", true);

        mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modelRequest("invalid_storage", sourceId, "invalid_table")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("模型只能绑定具有数据存储用途的数据源"));

        String first = mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modelRequest("first_model", storageId, "shared_table")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String modelId = JsonPath.read(first, "$.model.id");

        mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modelRequest("second_model", storageId, "shared_table")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("同一数据存储下的物理表位置已被其他模型使用"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/publish", modelId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("请先定义至少一个模型字段"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": [{
                                    "code": "title", "name": "标题", "fieldType": "STRING",
                                    "nullable": true, "primaryKey": false, "sortOrder": 10
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].fieldType").value("STRING"))
                .andExpect(jsonPath("$.fields[0].length").doesNotExist());
    }

    @Test
    void resolvesNamespaceFromStorageAndKeepsItAsAModelSnapshot() throws Exception {
        String postgresqlStorageId = createDataSource("namespace_pg", "PostgreSQL 存储", "STORAGE", true);
        String mysqlStorageId = createDataSource("namespace_mysql", "MySQL 存储", "STORAGE", true, "MYSQL", 3306);
        String modelId = JsonPath.read(mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modelRequest("namespace_model", postgresqlStorageId, "namespace_table")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.model.catalogName").value("warehouse"))
                .andExpect(jsonPath("$.model.schemaName").value("public"))
                .andReturn().getResponse().getContentAsString(), "$.model.id");

        mockMvc.perform(post("/api/v1/data-sources/{id}/actions/update", postgresqlStorageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "PostgreSQL 存储", "purposes": ["STORAGE"],
                                  "type": "POSTGRESQL", "enabled": true,
                                  "connection": {
                                    "kind": "JDBC", "host": "localhost", "port": 5432,
                                    "databaseName": "changed_database", "schemaName": "changed_schema", "username": "tester"
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/models/{id}/actions/update", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "命名空间快照模型", "storageDataSourceId": "%s",
                                  "physicalTableName": "namespace_table"
                                }
                                """.formatted(postgresqlStorageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model.catalogName").value("warehouse"))
                .andExpect(jsonPath("$.model.schemaName").value("public"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/update", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "命名空间快照模型", "storageDataSourceId": "%s",
                                  "physicalTableName": "namespace_table"
                                }
                                """.formatted(mysqlStorageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model.catalogName").value("warehouse"))
                .andExpect(jsonPath("$.model.schemaName").doesNotExist());
    }

    @Test
    void validatesAndExposesSingleNodeClickHouseSortingKeys() throws Exception {
        String storageId = createDataSource("clickhouse_storage", "ClickHouse 存储", "STORAGE", true, "CLICKHOUSE", 8123);
        mockMvc.perform(get("/api/v1/models/platform-types").param("storageDataSourceId", storageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[8].type").value("STRING"))
                .andExpect(jsonPath("$[8].supported").value(true))
                .andExpect(jsonPath("$[8].lengthParameterSupported").value(false))
                .andExpect(jsonPath("$[9].type").value("BINARY"))
                .andExpect(jsonPath("$[9].supported").value(false))
                .andExpect(jsonPath("$[11].type").value("TIMESTAMP"))
                .andExpect(jsonPath("$[11].supported").value(true))
                .andExpect(jsonPath("$[12].type").value("TIMESTAMP_NTZ"))
                .andExpect(jsonPath("$[12].supported").value(false));
        String created = mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "clickhouse_events", "name": "ClickHouse 事件模型",
                                  "storageDataSourceId": "%s",
                                  "physicalTableName": "events",
                                  "clickHouseOrderByColumns": ["event_time", "event_id"]
                                }
                                """.formatted(storageId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.model.catalogName").value("warehouse"))
                .andExpect(jsonPath("$.model.schemaName").doesNotExist())
                .andExpect(jsonPath("$.model.clickHouseOrderByColumns[0]").value("event_time"))
                .andExpect(jsonPath("$.model.clickHouseOrderByColumns[1]").value("event_id"))
                .andReturn().getResponse().getContentAsString();
        String modelId = JsonPath.read(created, "$.model.id");

        mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields": [{
                                  "code": "event_id", "name": "事件ID", "fieldType": "LONG",
                                  "nullable": false, "primaryKey": true, "sortOrder": 10
                                }]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("ClickHouse 排序键字段不存在：event_time"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields": [
                                  {
                                    "code": "event_id", "name": "事件ID", "fieldType": "LONG",
                                    "nullable": false, "primaryKey": true, "sortOrder": 10
                                  },
                                  {
                                    "code": "event_time", "name": "事件时间", "fieldType": "TIMESTAMP",
                                    "nullable": false, "primaryKey": false, "sortOrder": 20
                                  },
                                  {
                                    "code": "payload", "name": "载荷", "fieldType": "STRING",
                                    "nullable": true, "primaryKey": false, "sortOrder": 30
                                  }
                                ]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/models/{id}/actions/create-physical-table", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MATCHED"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/update", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "ClickHouse 事件模型", "storageDataSourceId": "%s",
                                  "physicalTableName": "events",
                                  "clickHouseOrderByColumns": ["event_id"]
                                }
                                """.formatted(storageId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("物理表已存在，ClickHouse 排序键需要通过后续换表流程调整，不能直接修改"));
    }

    @Test
    void persistsAndCancelsPhysicalTableChangePlansWithoutChangingTheCurrentModelFields() throws Exception {
        String storageId = createDataSource("change_storage", "变更存储", "STORAGE", true);
        String modelId = JsonPath.read(mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modelRequest("change_model", storageId, "change_table")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.model.id");
        String fields = mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields": [{
                                  "code": "order_id", "name": "订单ID", "fieldType": "LONG",
                                  "nullable": false, "primaryKey": true, "sortOrder": 10
                                }]}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String orderIdFieldId = JsonPath.read(fields, "$.fields[0].id");

        mockMvc.perform(post("/api/v1/models/{id}/actions/create-physical-table", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MATCHED"));

        String plan = mockMvc.perform(post("/api/v1/models/{id}/physical-table-change-plans", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields": [
                                  {
                                    "id": "%s", "code": "order_id", "name": "订单ID", "fieldType": "LONG",
                                    "nullable": false, "primaryKey": true, "sortOrder": 10
                                  },
                                  {
                                    "code": "remark", "name": "备注", "fieldType": "STRING", "length": 200,
                                    "nullable": true, "primaryKey": false, "sortOrder": 20
                                  }
                                ]}
                                """.formatted(orderIdFieldId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.plan.strategy").value("IN_PLACE"))
                .andExpect(jsonPath("$.plan.operations[0].type").value("ADD_COLUMN"))
                .andReturn().getResponse().getContentAsString();
        String planId = JsonPath.read(plan, "$.id");

        mockMvc.perform(get("/api/v1/models/{id}/physical-table-change-plans", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/models/{id}", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.length()").value(1))
                .andExpect(jsonPath("$.model.schemaVersion").value(2));

        mockMvc.perform(post("/api/v1/models/{id}/physical-table-change-plans/{planId}/actions/cancel", modelId, planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/v1/models/{id}", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.length()").value(1))
                .andExpect(jsonPath("$.model.schemaVersion").value(2));
    }

    @Test
    void appliesTargetFieldSnapshotOnlyAfterThePhysicalChangeExecutionSucceeds() throws Exception {
        String storageId = createDataSource("execute_storage", "执行存储", "STORAGE", true);
        String modelId = JsonPath.read(mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modelRequest("execute_model", storageId, "execute_table")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.model.id");
        String fields = mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields": [{
                                  "code": "order_id", "name": "订单ID", "fieldType": "LONG",
                                  "nullable": false, "primaryKey": true, "sortOrder": 10
                                }]}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String orderIdFieldId = JsonPath.read(fields, "$.fields[0].id");

        mockMvc.perform(post("/api/v1/models/{id}/actions/create-physical-table", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MATCHED"));

        String plan = mockMvc.perform(post("/api/v1/models/{id}/physical-table-change-plans", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields": [
                                  {
                                    "id": "%s", "code": "order_id", "name": "订单ID", "fieldType": "LONG",
                                    "nullable": false, "primaryKey": true, "sortOrder": 10
                                  },
                                  {
                                    "code": "remark", "name": "备注", "fieldType": "STRING", "length": 200,
                                    "nullable": true, "primaryKey": false, "sortOrder": 20
                                  }
                                ]}
                                """.formatted(orderIdFieldId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String planId = JsonPath.read(plan, "$.id");

        mockMvc.perform(post("/api/v1/models/{id}/physical-table-change-plans/{planId}/actions/execute", modelId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionMode\":\"IN_PLACE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.executionMode").value("IN_PLACE"));
        mockMvc.perform(get("/api/v1/models/{id}", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.length()").value(2))
                .andExpect(jsonPath("$.fields[1].code").value("remark"))
                .andExpect(jsonPath("$.model.schemaVersion").value(3));
    }

    private String createDataSource(String code, String name, String purpose, boolean enabled) throws Exception {
        return createDataSource(code, name, purpose, enabled, "POSTGRESQL", 5432);
    }

    private String createDataSource(
            String code,
            String name,
            String purpose,
            boolean enabled,
            String type,
            int port
    ) throws Exception {
        String response = mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s", "name": "%s", "purposes": ["%s"],
                                  "type": "%s", "enabled": %s,
                                  "connection": {
                                    "kind": "JDBC",
                                    "host": "localhost", "port": %s,
                                    "databaseName": "warehouse", "schemaName": "public", "username": "tester"
                                  }
                                }
                                """.formatted(code, name, purpose, type, enabled, port)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String createDirectory(String scope, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/directories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"%s\",\"name\":\"%s\",\"sortOrder\":10}".formatted(scope, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String modelRequest(String code, String storageId, String physicalTableName) {
        return """
                {
                  "code": "%s", "name": "%s", "storageDataSourceId": "%s",
                  "physicalTableName": "%s"
                }
                """.formatted(code, code, storageId, physicalTableName);
    }

    private void clearData() {
        fieldRepository.deleteAll();
        modelRepository.deleteAll();
        dataSourceRepository.deleteAll();
        directoryRepository.deleteAll();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PhysicalTableTestConfiguration {

        @Bean
        @Primary
        ModelPhysicalTablePort modelPhysicalTablePort() {
            return new ModelPhysicalTablePort() {
                private final java.util.Set<java.util.UUID> readyModelIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

                @Override
                public ModelPhysicalTableInspection inspect(
                        cn.superhuang.data.scalpel.business.datasource.domain.DataSource dataSource,
                        cn.superhuang.data.scalpel.business.model.domain.DataModel model,
                        java.util.List<cn.superhuang.data.scalpel.business.model.domain.DataModelField> fields
                ) {
                    assertNoManagementTransaction();
                    TableIdentifier table = new TableIdentifier(model.getCatalogName(), model.getSchemaName(), model.getPhysicalTableName());
                    boolean ready = readyModelIds.contains(model.getId()) || model.getPhysicalTableMode().name().equals("EXTERNAL");
                    return new ModelPhysicalTableInspection(
                            table,
                            ready ? PhysicalTableState.MATCHED : PhysicalTableState.NOT_FOUND,
                            ready,
                            ready ? "测试物理表已就绪" : "测试物理表尚未创建",
                            java.util.List.of()
                    );
                }

                @Override
                public TableMetadata readExternalTable(
                        cn.superhuang.data.scalpel.business.datasource.domain.DataSource dataSource,
                        cn.superhuang.data.scalpel.business.model.domain.DataModel model
                ) {
                    assertNoManagementTransaction();
                    return externalTableMetadata(new TableIdentifier(
                            model.getCatalogName(), model.getSchemaName(), model.getPhysicalTableName()
                    ));
                }

                @Override
                public TableMetadata readExternalTable(
                        cn.superhuang.data.scalpel.business.datasource.domain.DataSource dataSource,
                        TableIdentifier table
                ) {
                    assertNoManagementTransaction();
                    return externalTableMetadata(table);
                }

                private TableMetadata externalTableMetadata(TableIdentifier table) {
                    if (table.table().equals("unsupported_external")) {
                        return new TableMetadata(
                                new TableSummary(table, "TABLE", null),
                                List.of(new ColumnMetadata(
                                        "event_time", 1, Types.TIME, "time", LogicalType.TIME,
                                        null, null, null, false, null, false, false, null
                                )),
                                new PrimaryKeyMetadata(null, List.of()),
                                List.of()
                        );
                    }
                    return new TableMetadata(
                            new TableSummary(table, "TABLE", "外部订单表"),
                            List.of(
                                    new ColumnMetadata(
                                            "order_id", 1, Types.BIGINT, "int8", LogicalType.INTEGER,
                                            null, null, null, false, null, true, false, "订单主键"
                                    ),
                                    new ColumnMetadata(
                                            "amount", 2, Types.DECIMAL, "numeric", LogicalType.DECIMAL,
                                            null, 18, 2, true, null, false, false, "订单金额"
                                    ),
                                    new ColumnMetadata(
                                            "remark", 3, Types.VARCHAR, "varchar", LogicalType.STRING,
                                            200, null, null, true, null, false, false, "订单备注"
                                    )
                            ),
                            new PrimaryKeyMetadata("pk_external_orders", List.of("order_id")),
                            List.of()
                    );
                }

                @Override
                public DdlPlan planCreate(
                        cn.superhuang.data.scalpel.business.datasource.domain.DataSource dataSource,
                        cn.superhuang.data.scalpel.business.model.domain.DataModel model,
                        java.util.List<cn.superhuang.data.scalpel.business.model.domain.DataModelField> fields
                ) {
                    TableIdentifier table = new TableIdentifier(model.getCatalogName(), model.getSchemaName(), model.getPhysicalTableName());
                    return new DdlPlan(table, java.util.List.of("CREATE TABLE test_table"));
                }

                @Override
                public TableChangePlan planChange(
                        cn.superhuang.data.scalpel.business.datasource.domain.DataSource dataSource,
                        cn.superhuang.data.scalpel.business.model.domain.DataModel model,
                        TableDefinition before,
                        TableDefinition target
                ) {
                    assertNoManagementTransaction();
                    TableColumnDefinition targetColumn = target.columns().getLast();
                    return new TableChangePlan(
                            before,
                            target,
                            TableChangeStrategy.IN_PLACE,
                            TableChangeRisk.SAFE,
                            TableDdlAtomicity.TRANSACTIONAL_BATCH,
                            java.util.List.of(new TableChangeOperation(
                                    TableChangeOperationType.ADD_COLUMN,
                                    null,
                                    targetColumn,
                                    java.util.List.of(),
                                    java.util.List.of(),
                                    TableChangeStrategy.IN_PLACE,
                                    TableChangeRisk.SAFE,
                                    java.util.List.of(),
                                    java.util.List.of()
                            )),
                            java.util.List.of(),
                            java.util.List.of(),
                            java.util.List.of(new TableChangeExecutionOption(
                                    TableChangeExecutionMode.IN_PLACE,
                                    TableDdlAtomicity.TRANSACTIONAL_BATCH,
                                    java.util.List.of("ALTER TABLE test_table ADD COLUMN test_column varchar(100)")
                            ))
                    );
                }

                @Override
                public void executeChange(
                        cn.superhuang.data.scalpel.business.datasource.domain.DataSource dataSource,
                        cn.superhuang.data.scalpel.business.model.domain.DataModel model,
                        TableChangePlan plan,
                        TableChangeExecutionMode mode
                ) {
                    assertNoManagementTransaction();
                    // The state-machine integration test does not require a physical database.
                }

                @Override
                public ModelPhysicalTableInspection create(
                        cn.superhuang.data.scalpel.business.datasource.domain.DataSource dataSource,
                        cn.superhuang.data.scalpel.business.model.domain.DataModel model,
                        java.util.List<cn.superhuang.data.scalpel.business.model.domain.DataModelField> fields
                ) {
                    assertNoManagementTransaction();
                    readyModelIds.add(model.getId());
                    return inspect(dataSource, model, fields);
                }

                @Override
                public StandardQueryResult query(
                        cn.superhuang.data.scalpel.business.datasource.domain.DataSource dataSource,
                        cn.superhuang.data.scalpel.business.model.domain.DataModel model,
                        StandardQuery query,
                        int maximumRows,
                        Duration timeout
                ) {
                    assertNoManagementTransaction();
                    return new StandardQueryResult(null, List.of(Map.of("order_id", "1001")));
                }

                private void assertNoManagementTransaction() {
                    assertFalse(
                            TransactionSynchronizationManager.isActualTransactionActive(),
                            "外部物理数据库调用不得处于管理数据库事务中"
                    );
                }
            };
        }
    }
}
