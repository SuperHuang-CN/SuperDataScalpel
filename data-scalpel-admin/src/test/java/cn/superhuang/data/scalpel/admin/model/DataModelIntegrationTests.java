package cn.superhuang.data.scalpel.admin.model;

import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.directory.repository.DirectoryRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTableInspection;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTablePort;
import cn.superhuang.data.scalpel.business.model.service.PhysicalTableState;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.dialect.model.DdlPlan;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.model.IndexMetadata;
import cn.superhuang.data.scalpel.dialect.model.PrimaryKeyMetadata;
import cn.superhuang.data.scalpel.dialect.model.SpatialColumnMetadata;
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
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Types;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
        PhysicalTableTestConfiguration.reset();
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

        String clickHouseFields = mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
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
        mockMvc.perform(post("/api/v1/models/{id}/actions/publish", modelId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("只有草稿或已停用模型可以发布"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("已发布模型请先停用后再修改字段"));

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
        mockMvc.perform(post("/api/v1/models/{id}/actions/publish", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model.status").value("PUBLISHED"));
        mockMvc.perform(post("/api/v1/models/{id}/actions/enable", modelId))
                .andExpect(status().isNotFound());

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
    void importsExternalTableFromSourceOnlyJdbcDataSource() throws Exception {
        String sourceId = createDataSource("external_source", "外部表来源", "SOURCE", true);

        mockMvc.perform(get("/api/v1/models/external-table-import-preview")
                        .param("storageDataSourceId", sourceId)
                        .param("physicalTableName", "external_orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importable").value(true))
                .andExpect(jsonPath("$.columns.length()").value(3));

        String created = mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "source_external_order", "name": "来源库外部订单模型",
                                  "storageDataSourceId": "%s", "physicalTableName": "external_orders",
                                  "physicalTableMode": "EXTERNAL"
                                }
                                """.formatted(sourceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.model.physicalTableMode").value("EXTERNAL"))
                .andExpect(jsonPath("$.model.storageDataSourceId").value(sourceId))
                .andExpect(jsonPath("$.fields.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        String modelId = JsonPath.read(created, "$.model.id");

        mockMvc.perform(post("/api/v1/models/{id}/actions/publish", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model.status").value("PUBLISHED"));

        String missingCreated = mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "missing_external_order", "name": "缺失外部表模型",
                                  "storageDataSourceId": "%s", "physicalTableName": "missing_external",
                                  "physicalTableMode": "EXTERNAL"
                                }
                                """.formatted(sourceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String missingModelId = JsonPath.read(missingCreated, "$.model.id");
        mockMvc.perform(post("/api/v1/models/{id}/actions/publish", missingModelId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("物理表未就绪：测试物理表尚未创建"));
        assertEquals(0, PhysicalTableTestConfiguration.createCalls());
    }

    @Test
    void previewsJdbcStructureAndCreatesManagedDraftWithoutCreatingThePhysicalTable() throws Exception {
        String sourceId = createDataSource("managed_import_source", "受管模型导入来源", "SOURCE", true);
        String storageId = createDataSource("managed_import_storage", "受管模型目标存储", "STORAGE", true);
        String directoryId = createDirectory("MODEL", "导入模型");

        mockMvc.perform(post("/api/v1/models/managed-import-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceDataSourceId": "%s",
                                  "sourceTable": {"catalog": "warehouse", "schema": "public", "table": "external_orders"},
                                  "targetStorageDataSourceId": "%s"
                                }
                                """.formatted(sourceId, storageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceTable.table").value("external_orders"))
                .andExpect(jsonPath("$.suggestedCode").value("external_orders"))
                .andExpect(jsonPath("$.suggestedName").value("外部订单表"))
                .andExpect(jsonPath("$.suggestedPhysicalTableName").value("external_orders"))
                .andExpect(jsonPath("$.tableImportable").value(true))
                .andExpect(jsonPath("$.importable").value(true))
                .andExpect(jsonPath("$.columns.length()").value(3))
                .andExpect(jsonPath("$.columns[0].code").value("order_id"))
                .andExpect(jsonPath("$.columns[0].fieldType").value("LONG"))
                .andExpect(jsonPath("$.columns[0].primaryKey").value(true))
                .andExpect(jsonPath("$.columns[1].precision").value(18))
                .andExpect(jsonPath("$.columns[2].length").value(200))
                .andExpect(jsonPath("$.warnings[0]").value("字段 order_id 的自增属性未导入"));

        String created = mockMvc.perform(post("/api/v1/models/managed-drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "external_orders", "name": "外部订单表", "directoryId": "%s",
                                  "storageDataSourceId": "%s", "physicalTableName": "imported_orders",
                                  "clickHouseOrderByColumns": [],
                                  "fields": [
                                    {
                                      "code": "order_id", "name": "订单主键", "fieldType": "LONG",
                                      "nullable": false, "primaryKey": true, "sortOrder": 10, "description": "订单主键"
                                    },
                                    {
                                      "code": "amount", "name": "订单金额", "fieldType": "DECIMAL",
                                      "precision": 18, "scale": 2, "nullable": true, "primaryKey": false,
                                      "sortOrder": 20
                                    },
                                    {
                                      "code": "remark", "name": "订单备注", "fieldType": "STRING", "length": 200,
                                      "nullable": true, "primaryKey": false, "sortOrder": 30
                                    }
                                  ]
                                }
                                """.formatted(directoryId, storageId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.model.physicalTableMode").value("MANAGED"))
                .andExpect(jsonPath("$.model.status").value("DRAFT"))
                .andExpect(jsonPath("$.model.storageDataSourceId").value(storageId))
                .andExpect(jsonPath("$.model.physicalTableName").value("imported_orders"))
                .andExpect(jsonPath("$.fields.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        String modelId = JsonPath.read(created, "$.model.id");

        assertEquals(0, PhysicalTableTestConfiguration.createCalls());
        mockMvc.perform(get("/api/v1/models/{id}/physical-table", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_FOUND"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/publish", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model.status").value("PUBLISHED"));
        assertEquals(1, PhysicalTableTestConfiguration.createCalls());
    }

    @Test
    void reportsUnresolvedManagedImportColumnsAndRejectsInvalidTargetsWithoutPartialModels() throws Exception {
        String sourceId = createDataSource("managed_problem_source", "问题字段来源", "DISTRIBUTION", true);
        String disabledSourceId = createDataSource("managed_disabled_source", "停用来源", "SOURCE", false);
        String storageId = createDataSource("managed_problem_storage", "问题字段目标", "STORAGE", true);
        String clickHouseStorageId = createDataSource(
                "managed_clickhouse_storage", "ClickHouse 问题字段目标", "STORAGE", true, "CLICKHOUSE", 8123
        );
        String nonStorageId = createDataSource("managed_non_storage", "非存储目标", "SOURCE", true);

        mockMvc.perform(post("/api/v1/models/managed-import-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceDataSourceId": "%s",
                                  "sourceTable": {"catalog": "warehouse", "schema": "public", "table": "problem_columns"},
                                  "targetStorageDataSourceId": "%s"
                                }
                                """.formatted(sourceId, storageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importable").value(false))
                .andExpect(jsonPath("$.columns[0].code").value(""))
                .andExpect(jsonPath("$.columns[1].code").value(""))
                .andExpect(jsonPath("$.columns[2].code").value(""))
                .andExpect(jsonPath("$.columns[3].fieldType").doesNotExist())
                .andExpect(jsonPath("$.columns[3].importable").value(false))
                .andExpect(jsonPath("$.warnings.length()").value(2));

        mockMvc.perform(post("/api/v1/models/managed-import-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceDataSourceId": "%s",
                                  "sourceTable": {"catalog": "warehouse", "schema": "public", "table": "binary_source"},
                                  "targetStorageDataSourceId": "%s"
                                }
                                """.formatted(sourceId, clickHouseStorageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importable").value(false))
                .andExpect(jsonPath("$.columns[0].fieldType").doesNotExist())
                .andExpect(jsonPath("$.columns[0].mappingQuality").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.columns[0].issues[0]").value(
                        org.hamcrest.Matchers.containsString("目标数据存储")
                ));

        mockMvc.perform(post("/api/v1/models/managed-import-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceDataSourceId": "%s",
                                  "sourceTable": {"catalog": "warehouse", "schema": "public", "table": "source_view"},
                                  "targetStorageDataSourceId": "%s"
                                }
                                """.formatted(sourceId, storageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableImportable").value(false))
                .andExpect(jsonPath("$.importable").value(false))
                .andExpect(jsonPath("$.tableIssues[0]").value(
                        org.hamcrest.Matchers.containsString("只能导入普通物理表")
                ));

        mockMvc.perform(post("/api/v1/models/managed-import-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceDataSourceId": "%s",
                                  "sourceTable": {"table": "external_orders"},
                                  "targetStorageDataSourceId": "%s"
                                }
                                """.formatted(disabledSourceId, storageId)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/models/managed-import-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceDataSourceId": "%s",
                                  "sourceTable": {"table": "external_orders"},
                                  "targetStorageDataSourceId": "%s"
                                }
                                """.formatted(sourceId, nonStorageId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/models/managed-drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "existing_target_model", "name": "目标冲突模型",
                                  "storageDataSourceId": "%s", "physicalTableName": "existing_target",
                                  "fields": [{
                                    "code": "id", "name": "ID", "fieldType": "LONG",
                                    "nullable": false, "primaryKey": true, "sortOrder": 10
                                  }]
                                }
                                """.formatted(storageId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("目标物理表必须不存在")));
        assertEquals(0, modelRepository.count());
        assertEquals(0, fieldRepository.count());
        assertEquals(0, PhysicalTableTestConfiguration.createCalls());
    }

    @Test
    void downloadsMetadataTemplateAndPreviewsExcelAsManagedDraftsWithoutDdl() throws Exception {
        String storageId = createDataSource("metadata_import_storage", "元数据导入存储", "STORAGE", true);

        byte[] template = mockMvc.perform(get("/api/v1/models/metadata-import-template"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        byte[] workbook = metadataWorkbook(template, "metadata_orders", "元数据订单模型", "metadata_orders_table");

        String preview = mockMvc.perform(multipart("/api/v1/models/actions/preview-metadata-import")
                        .file(new MockMultipartFile(
                                "file", "models.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook
                        ))
                        .param("targetStorageDataSourceId", storageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formatVersion").value(2))
                .andExpect(jsonPath("$.importable").value(true))
                .andExpect(jsonPath("$.models[0].code").value("metadata_orders"))
                .andExpect(jsonPath("$.models[0].physicalTableName").value("metadata_orders_table"))
                .andExpect(jsonPath("$.models[0].clickHouseOrderByColumns.length()").value(0))
                .andExpect(jsonPath("$.models[0].warnings[0]").value(
                        org.hamcrest.Matchers.containsString("ClickHouse 排序键已忽略")
                ))
                .andExpect(jsonPath("$.models[0].fields.length()").value(2))
                .andExpect(jsonPath("$.models[0].fields[0].fieldType").value("LONG"))
                .andExpect(jsonPath("$.models[0].fields[0].primaryKey").value(true))
                .andExpect(jsonPath("$.models[0].fields[1].fieldType").value("DECIMAL"))
                .andReturn().getResponse().getContentAsString();

        assertEquals("metadata_orders", JsonPath.read(preview, "$.models[0].code"));
        assertEquals(0, modelRepository.count());
        assertEquals(0, fieldRepository.count());
        assertEquals(0, PhysicalTableTestConfiguration.createCalls());
    }

    @Test
    void rejectsNonXlsxModelMetadataImportFiles() throws Exception {
        String storageId = createDataSource("metadata_xls_storage", "旧版 Excel 导入存储", "STORAGE", true);

        mockMvc.perform(multipart("/api/v1/models/actions/preview-metadata-import")
                        .file(new MockMultipartFile(
                                "file", "models.xls", "application/vnd.ms-excel", new byte[]{1, 2, 3}
                        ))
                        .param("targetStorageDataSourceId", storageId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("只支持 .xlsx")));

        assertEquals(0, modelRepository.count());
        assertEquals(0, fieldRepository.count());
        assertEquals(0, PhysicalTableTestConfiguration.createCalls());
    }

    @Test
    void importsLegacyV1ScalarMetadataAndV2GeometryMetadata() throws Exception {
        String storageId = createDataSource("metadata_versions_storage", "元数据版本存储", "STORAGE", true);

        mockMvc.perform(multipart("/api/v1/models/actions/preview-metadata-import")
                        .file(new MockMultipartFile(
                                "file", "legacy-v1.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                legacyMetadataWorkbook()
                        ))
                        .param("targetStorageDataSourceId", storageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formatVersion").value(1))
                .andExpect(jsonPath("$.importable").value(true))
                .andExpect(jsonPath("$.models[0].fields[0].fieldType").value("LONG"))
                .andExpect(jsonPath("$.models[0].fields[0].geometry").doesNotExist());

        byte[] template = mockMvc.perform(get("/api/v1/models/metadata-import-template"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        mockMvc.perform(multipart("/api/v1/models/actions/preview-metadata-import")
                        .file(new MockMultipartFile(
                                "file", "geometry-v2.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                geometryMetadataWorkbook(template)
                        ))
                        .param("targetStorageDataSourceId", storageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formatVersion").value(2))
                .andExpect(jsonPath("$.importable").value(true))
                .andExpect(jsonPath("$.models[0].fields[0].fieldType").value("GEOMETRY"))
                .andExpect(jsonPath("$.models[0].fields[0].geometry.kind").value("MULTIPOLYGON"))
                .andExpect(jsonPath("$.models[0].fields[0].geometry.crs.authority").value("EPSG"))
                .andExpect(jsonPath("$.models[0].fields[0].geometry.crs.code").value(4326))
                .andExpect(jsonPath("$.models[0].fields[0].geometry.dimension").value("XY"))
                .andExpect(jsonPath("$.models[0].fields[0].primaryKey").value(false));

        assertEquals(0, modelRepository.count());
        assertEquals(0, fieldRepository.count());
        assertEquals(0, PhysicalTableTestConfiguration.createCalls());
    }

    @Test
    void managesGeometryFieldsAndEnforcesV1QueryAndPhysicalChangeBoundaries() throws Exception {
        String storageId = createDataSource("geometry_storage", "空间模型存储", "STORAGE", true);
        mockMvc.perform(get("/api/v1/models/platform-types").param("storageDataSourceId", storageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[13].type").value("GEOMETRY"))
                .andExpect(jsonPath("$[13].supported").value(true))
                .andExpect(jsonPath("$[13].geometryKinds.length()").value(8))
                .andExpect(jsonPath("$[13].coordinateDimensions[0]").value("XY"))
                .andExpect(jsonPath("$[13].crsAuthorities[0]").value("EPSG"));

        String created = mockMvc.perform(post("/api/v1/models/managed-drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "spatial_asset", "name": "空间资产",
                                  "storageDataSourceId": "%s", "physicalTableName": "spatial_asset",
                                  "fields": [
                                    {
                                      "code": "asset_id", "name": "资产ID", "fieldType": "LONG",
                                      "nullable": false, "primaryKey": true, "sortOrder": 10
                                    },
                                    {
                                      "code": "shape", "name": "空间位置", "fieldType": "GEOMETRY",
                                      "geometry": {
                                        "kind": "POINT",
                                        "crs": {"authority": "epsg", "code": 4326},
                                        "dimension": "XY"
                                      },
                                      "nullable": true, "primaryKey": false, "sortOrder": 20
                                    }
                                  ]
                                }
                                """.formatted(storageId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fields[1].fieldType").value("GEOMETRY"))
                .andExpect(jsonPath("$.fields[1].geometry.kind").value("POINT"))
                .andExpect(jsonPath("$.fields[1].geometry.crs.authority").value("EPSG"))
                .andExpect(jsonPath("$.fields[1].geometry.crs.code").value(4326))
                .andExpect(jsonPath("$.fields[1].geometry.dimension").value("XY"))
                .andReturn().getResponse().getContentAsString();
        String modelId = JsonPath.read(created, "$.model.id");
        String idFieldId = JsonPath.read(created, "$.fields[0].id");
        String geometryFieldId = JsonPath.read(created, "$.fields[1].id");

        assertEquals(
                "POINT",
                fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(
                                java.util.UUID.fromString(modelId)
                        ).get(1).getGeometry().kind().name()
        );

        mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields": [{
                                  "code": "invalid_shape", "name": "非法主键", "fieldType": "GEOMETRY",
                                  "geometry": {
                                    "kind": "POINT",
                                    "crs": {"authority": "EPSG", "code": 4326},
                                    "dimension": "XY"
                                  },
                                  "nullable": false, "primaryKey": true, "sortOrder": 10
                                }]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("Geometry 字段不能作为主键")
                ));

        mockMvc.perform(post("/api/v1/models/{id}/actions/create-physical-table", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MATCHED"));

        mockMvc.perform(get("/api/v1/models/{id}/data-preview", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.length()").value(1))
                .andExpect(jsonPath("$.columns[0].code").value("asset_id"));
        assertEquals(
                List.of("asset_id"),
                PhysicalTableTestConfiguration.lastQuery().projections().stream()
                        .map(projection -> projection.alias()).toList()
        );

        mockMvc.perform(post("/api/v1/models/{id}/actions/query-data", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pageNo":1,"pageSize":20,"columns":["shape"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("返回字段不支持该字段")
                ));
        mockMvc.perform(post("/api/v1/models/{id}/actions/query-data", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageNo":1,"pageSize":20,
                                  "filters":[{"field":"shape","operator":"EQ","value":"POINT(0 0)"}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("过滤字段不支持该字段")
                ));

        mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields": [
                                  {
                                    "id":"%s", "code":"asset_id", "name":"资产编号", "fieldType":"LONG",
                                    "nullable":false, "primaryKey":true, "sortOrder":20
                                  },
                                  {
                                    "id":"%s", "code":"shape", "name":"定位点", "fieldType":"GEOMETRY",
                                    "geometry":{
                                      "kind":"POINT",
                                      "crs":{"authority":"EPSG","code":4326},
                                      "dimension":"XY"
                                    },
                                    "nullable":true, "primaryKey":false, "sortOrder":10,
                                    "description":"仅保存结构定义"
                                  }
                                ]}
                                """.formatted(idFieldId, geometryFieldId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].name").value("定位点"))
                .andExpect(jsonPath("$.fields[0].description").value("仅保存结构定义"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields": [
                                  {
                                    "id":"%s", "code":"asset_id", "name":"资产编号", "fieldType":"LONG",
                                    "nullable":false, "primaryKey":true, "sortOrder":20
                                  },
                                  {
                                    "id":"%s", "code":"shape", "name":"定位点", "fieldType":"GEOMETRY",
                                    "geometry":{
                                      "kind":"POINT",
                                      "crs":{"authority":"EPSG","code":4326},
                                      "dimension":"XY"
                                    },
                                    "nullable":false, "primaryKey":false, "sortOrder":10
                                  }
                                ]}
                                """.formatted(idFieldId, geometryFieldId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("请先生成并执行物理表变更计划")
                ));

        mockMvc.perform(post("/api/v1/models/{id}/physical-table-change-plans", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields": [
                                  {
                                    "id":"%s", "code":"asset_id", "name":"资产编号", "fieldType":"LONG",
                                    "nullable":false, "primaryKey":true, "sortOrder":20
                                  },
                                  {
                                    "id":"%s", "code":"shape", "name":"定位点", "fieldType":"GEOMETRY",
                                    "geometry":{
                                      "kind":"POINT",
                                      "crs":{"authority":"EPSG","code":4326},
                                      "dimension":"XY"
                                    },
                                    "nullable":false, "primaryKey":false, "sortOrder":10
                                  }
                                ]}
                                """.formatted(idFieldId, geometryFieldId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLANNED"));

        mockMvc.perform(post("/api/v1/models/{id}/physical-table-change-plans", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields": [
                                  {
                                    "id":"%s", "code":"asset_id", "name":"资产编号", "fieldType":"LONG",
                                    "nullable":false, "primaryKey":true, "sortOrder":20
                                  },
                                  {
                                    "id":"%s", "code":"shape", "name":"定位点", "fieldType":"GEOMETRY",
                                    "geometry":{
                                      "kind":"POINT",
                                      "crs":{"authority":"EPSG","code":3857},
                                      "dimension":"XY"
                                    },
                                    "nullable":true, "primaryKey":false, "sortOrder":10
                                  }
                                ]}
                                """.formatted(idFieldId, geometryFieldId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("仅支持修改可空性和非空间字段主键约束")
                ));
    }

    @Test
    void importsGeometryFromAnExternalPostGisTableWithoutFlatteningSpatialParameters() throws Exception {
        String storageId = createDataSource("geometry_external_storage", "空间外部表存储", "STORAGE", true);

        mockMvc.perform(get("/api/v1/models/external-table-import-preview")
                        .param("storageDataSourceId", storageId)
                        .param("physicalTableName", "geometry_source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importable").value(true))
                .andExpect(jsonPath("$.columns[0].platformType").value("GEOMETRY"))
                .andExpect(jsonPath("$.columns[0].geometry.kind").value("MULTIPOLYGON"))
                .andExpect(jsonPath("$.columns[0].geometry.crs.authority").value("EPSG"))
                .andExpect(jsonPath("$.columns[0].geometry.crs.code").value(4490))
                .andExpect(jsonPath("$.columns[0].geometry.dimension").value("XY"));

        mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"geometry_external", "name":"空间外部表",
                                  "storageDataSourceId":"%s", "physicalTableName":"geometry_source",
                                  "physicalTableMode":"EXTERNAL"
                                }
                                """.formatted(storageId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fields[0].fieldType").value("GEOMETRY"))
                .andExpect(jsonPath("$.fields[0].geometry.kind").value("MULTIPOLYGON"))
                .andExpect(jsonPath("$.fields[0].geometry.crs.code").value(4490))
                .andExpect(jsonPath("$.fields[0].primaryKey").value(false));
    }

    @Test
    void exportsOnlyManagedModelMetadataWithoutEnvironmentBindings() throws Exception {
        String storageId = createDataSource("metadata_export_storage", "元数据导出存储", "STORAGE", true);
        String managed = mockMvc.perform(post("/api/v1/models/managed-drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "metadata_export", "name": "元数据导出模型",
                                  "storageDataSourceId": "%s", "physicalTableName": "metadata_export_table",
                                  "clickHouseOrderByColumns": [], "description": "只导出平台表结构",
                                  "fields": [{
                                    "code": "id", "name": "主键", "fieldType": "LONG",
                                    "nullable": false, "primaryKey": true, "sortOrder": 10
                                  }, {
                                    "code": "shape", "name": "位置", "fieldType": "GEOMETRY",
                                    "geometry": {
                                      "kind": "POINT",
                                      "crs": {"authority": "EPSG", "code": 4326},
                                      "dimension": "XY"
                                    },
                                    "nullable": true, "primaryKey": false, "sortOrder": 20
                                  }]
                                }
                                """.formatted(storageId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String managedId = JsonPath.read(managed, "$.model.id");

        byte[] exported = mockMvc.perform(post("/api/v1/models/actions/export-metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modelIds":["%s"]}
                                """.formatted(managedId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            assertEquals("DATASCALPEL_MODEL_METADATA", workbook.getSheet("说明").getRow(0).getCell(1).getStringCellValue());
            assertEquals("metadata_export", workbook.getSheet("模型").getRow(1).getCell(0).getStringCellValue());
            assertEquals("metadata_export_table", workbook.getSheet("模型").getRow(1).getCell(2).getStringCellValue());
            assertEquals("id", workbook.getSheet("字段").getRow(1).getCell(1).getStringCellValue());
            assertEquals("LONG", workbook.getSheet("字段").getRow(1).getCell(3).getStringCellValue());
            assertEquals("2", workbook.getSheet("说明").getRow(1).getCell(1).getStringCellValue());
            assertEquals("shape", workbook.getSheet("字段").getRow(2).getCell(1).getStringCellValue());
            assertEquals("GEOMETRY", workbook.getSheet("字段").getRow(2).getCell(3).getStringCellValue());
            assertEquals("POINT", workbook.getSheet("字段").getRow(2).getCell(7).getStringCellValue());
            assertEquals("EPSG", workbook.getSheet("字段").getRow(2).getCell(8).getStringCellValue());
            assertEquals(4326, (int) workbook.getSheet("字段").getRow(2).getCell(9).getNumericCellValue());
            assertEquals("XY", workbook.getSheet("字段").getRow(2).getCell(10).getStringCellValue());
            assertEquals(15, workbook.getSheet("字段").getRow(0).getLastCellNum());
            assertEquals(5, workbook.getSheet("模型").getRow(0).getLastCellNum());
        }

        String external = mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "metadata_external", "name": "外部模型不导出",
                                  "storageDataSourceId": "%s", "physicalTableName": "external_orders",
                                  "physicalTableMode": "EXTERNAL"
                                }
                                """.formatted(storageId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String externalId = JsonPath.read(external, "$.model.id");
        mockMvc.perform(post("/api/v1/models/actions/export-metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modelIds":["%s"]}
                                """.formatted(externalId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("EXTERNAL")));
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
                .andExpect(jsonPath("$[8].lengthParameterSupported").value(true))
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
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String eventIdFieldId = JsonPath.read(clickHouseFields, "$.fields[0].id");
        String eventTimeFieldId = JsonPath.read(clickHouseFields, "$.fields[1].id");
        String payloadFieldId = JsonPath.read(clickHouseFields, "$.fields[2].id");

        mockMvc.perform(post("/api/v1/models/{id}/actions/create-physical-table", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MATCHED"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields": [
                                  {
                                    "id": "%s", "code": "event_id", "name": "事件ID", "fieldType": "LONG",
                                    "nullable": false, "primaryKey": false, "sortOrder": 10
                                  },
                                  {
                                    "id": "%s", "code": "event_time", "name": "事件时间", "fieldType": "TIMESTAMP",
                                    "nullable": false, "primaryKey": false, "sortOrder": 20
                                  },
                                  {
                                    "id": "%s", "code": "payload", "name": "载荷", "fieldType": "STRING",
                                    "nullable": true, "primaryKey": false, "sortOrder": 30
                                  }
                                ]}
                                """.formatted(eventIdFieldId, eventTimeFieldId, payloadFieldId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].primaryKey").value(false));

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
    void allowsDisabledModelMetadataUpdatesAndRoutesStructuralUpdatesThroughAChangePlan() throws Exception {
        String storageId = createDataSource("disabled_change_storage", "停用模型变更存储", "STORAGE", true);
        String modelId = JsonPath.read(mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modelRequest("disabled_change_model", storageId, "disabled_change_table")))
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
        mockMvc.perform(post("/api/v1/models/{id}/actions/publish", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model.status").value("PUBLISHED"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields": [{
                                  "id": "%s", "code": "order_id", "name": "订单主键", "fieldType": "LONG",
                                  "nullable": false, "primaryKey": true, "sortOrder": 20
                                }]}
                                """.formatted(orderIdFieldId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("已发布模型请先停用后再修改字段"));

        mockMvc.perform(post("/api/v1/models/{id}/actions/disable", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model.status").value("DISABLED"));
        mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields": [{
                                  "id": "%s", "code": "order_id", "name": "订单主键", "fieldType": "LONG",
                                  "nullable": false, "primaryKey": true, "sortOrder": 20,
                                  "description": "停用后可直接修改业务信息"
                                }]}
                                """.formatted(orderIdFieldId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model.status").value("DISABLED"))
                .andExpect(jsonPath("$.fields[0].name").value("订单主键"))
                .andExpect(jsonPath("$.fields[0].description").value("停用后可直接修改业务信息"));

        String targetFields = """
                {"fields": [
                  {
                    "id": "%s", "code": "order_id", "name": "订单主键", "fieldType": "LONG",
                    "nullable": false, "primaryKey": true, "sortOrder": 20,
                    "description": "停用后可直接修改业务信息"
                  },
                  {
                    "code": "remark", "name": "备注", "fieldType": "STRING", "length": 200,
                    "nullable": true, "primaryKey": false, "sortOrder": 30
                  }
                ]}
                """.formatted(orderIdFieldId);
        mockMvc.perform(post("/api/v1/models/{id}/actions/update-fields", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(targetFields))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("受管物理表已存在，请先生成并执行物理表变更计划"));

        String planId = JsonPath.read(mockMvc.perform(
                        post("/api/v1/models/{id}/physical-table-change-plans", modelId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(targetFields))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(post(
                        "/api/v1/models/{id}/physical-table-change-plans/{planId}/actions/execute",
                        modelId,
                        planId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionMode\":\"IN_PLACE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mockMvc.perform(get("/api/v1/models/{id}", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model.status").value("DISABLED"))
                .andExpect(jsonPath("$.fields.length()").value(2))
                .andExpect(jsonPath("$.fields[1].code").value("remark"));
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

    private byte[] metadataWorkbook(byte[] template, String code, String name, String tableName) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var modelRow = workbook.getSheet("模型").createRow(1);
            modelRow.createCell(0).setCellValue(code);
            modelRow.createCell(1).setCellValue(name);
            modelRow.createCell(2).setCellValue(tableName);
            modelRow.createCell(3).setCellValue("来自固定 Excel 模板");
            modelRow.createCell(4).setCellValue("order_id");

            var idRow = workbook.getSheet("字段").createRow(1);
            idRow.createCell(0).setCellValue(code);
            idRow.createCell(1).setCellValue("order_id");
            idRow.createCell(2).setCellValue("订单主键");
            idRow.createCell(3).setCellValue("LONG");
            idRow.createCell(11).setCellValue("否");
            idRow.createCell(12).setCellValue("是");
            idRow.createCell(13).setCellValue(10);

            var amountRow = workbook.getSheet("字段").createRow(2);
            amountRow.createCell(0).setCellValue(code);
            amountRow.createCell(1).setCellValue("amount");
            amountRow.createCell(2).setCellValue("订单金额");
            amountRow.createCell(3).setCellValue("DECIMAL");
            amountRow.createCell(5).setCellValue(18);
            amountRow.createCell(6).setCellValue(2);
            amountRow.createCell(11).setCellValue("是");
            amountRow.createCell(12).setCellValue("否");
            amountRow.createCell(13).setCellValue(20);

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] geometryMetadataWorkbook(byte[] template) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var modelRow = workbook.getSheet("模型").createRow(1);
            modelRow.createCell(0).setCellValue("geometry_excel");
            modelRow.createCell(1).setCellValue("Excel 空间模型");
            modelRow.createCell(2).setCellValue("geometry_excel_table");

            var fieldRow = workbook.getSheet("字段").createRow(1);
            fieldRow.createCell(0).setCellValue("geometry_excel");
            fieldRow.createCell(1).setCellValue("shape");
            fieldRow.createCell(2).setCellValue("行政区");
            fieldRow.createCell(3).setCellValue("GEOMETRY");
            fieldRow.createCell(7).setCellValue("MULTIPOLYGON");
            fieldRow.createCell(8).setCellValue("epsg");
            fieldRow.createCell(9).setCellValue(4326);
            fieldRow.createCell(10).setCellValue("XY");
            fieldRow.createCell(11).setCellValue("是");
            fieldRow.createCell(12).setCellValue("否");
            fieldRow.createCell(13).setCellValue(10);

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] legacyMetadataWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var instructions = workbook.createSheet("说明");
            instructions.createRow(0).createCell(0).setCellValue("文件标识");
            instructions.getRow(0).createCell(1).setCellValue("DATASCALPEL_MODEL_METADATA");
            instructions.createRow(1).createCell(0).setCellValue("格式版本");
            instructions.getRow(1).createCell(1).setCellValue("1");

            var models = workbook.createSheet("模型");
            var modelHeaders = models.createRow(0);
            List<String> modelHeaderNames = List.of(
                    "模型编码*", "模型名称*", "目标物理表名*", "模型说明", "ClickHouse排序键"
            );
            for (int index = 0; index < modelHeaderNames.size(); index++) {
                modelHeaders.createCell(index).setCellValue(modelHeaderNames.get(index));
            }
            var modelRow = models.createRow(1);
            modelRow.createCell(0).setCellValue("legacy_model");
            modelRow.createCell(1).setCellValue("旧版模型");
            modelRow.createCell(2).setCellValue("legacy_model_table");

            var fields = workbook.createSheet("字段");
            var fieldHeaders = fields.createRow(0);
            List<String> fieldHeaderNames = List.of(
                    "模型编码*", "字段编码*", "字段名称*", "平台字段类型*", "长度", "精度", "小数位",
                    "是否可空*", "是否主键*", "排序值*", "字段说明"
            );
            for (int index = 0; index < fieldHeaderNames.size(); index++) {
                fieldHeaders.createCell(index).setCellValue(fieldHeaderNames.get(index));
            }
            var fieldRow = fields.createRow(1);
            fieldRow.createCell(0).setCellValue("legacy_model");
            fieldRow.createCell(1).setCellValue("id");
            fieldRow.createCell(2).setCellValue("主键");
            fieldRow.createCell(3).setCellValue("LONG");
            fieldRow.createCell(7).setCellValue("否");
            fieldRow.createCell(8).setCellValue("是");
            fieldRow.createCell(9).setCellValue(10);

            workbook.write(output);
            return output.toByteArray();
        }
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

        private static final java.util.Set<java.util.UUID> READY_MODEL_IDS = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private static final AtomicInteger CREATE_CALLS = new AtomicInteger();
        private static final AtomicReference<StandardQuery> LAST_QUERY = new AtomicReference<>();

        static void reset() {
            READY_MODEL_IDS.clear();
            CREATE_CALLS.set(0);
            LAST_QUERY.set(null);
        }

        static int createCalls() {
            return CREATE_CALLS.get();
        }

        static StandardQuery lastQuery() {
            return LAST_QUERY.get();
        }

        @Bean
        @Primary
        ModelPhysicalTablePort modelPhysicalTablePort() {
            return new ModelPhysicalTablePort() {
                @Override
                public ModelPhysicalTableInspection inspect(
                        cn.superhuang.data.scalpel.business.datasource.domain.DataSource dataSource,
                        cn.superhuang.data.scalpel.business.model.domain.DataModel model,
                        java.util.List<cn.superhuang.data.scalpel.business.model.domain.DataModelField> fields
                ) {
                    assertNoManagementTransaction();
                    TableIdentifier table = new TableIdentifier(model.getCatalogName(), model.getSchemaName(), model.getPhysicalTableName());
                    boolean ready = (model.getId() != null && READY_MODEL_IDS.contains(model.getId()))
                            || (model.getPhysicalTableMode().name().equals("EXTERNAL")
                                && !model.getPhysicalTableName().equals("missing_external"))
                            || model.getPhysicalTableName().equals("existing_target");
                    return new ModelPhysicalTableInspection(
                            table,
                            ready ? PhysicalTableState.MATCHED : PhysicalTableState.NOT_FOUND,
                            ready,
                            ready ? "测试物理表已就绪" : "测试物理表尚未创建",
                            java.util.List.of()
                    );
                }

                @Override
                public ModelPhysicalTableInspection inspect(
                        cn.superhuang.data.scalpel.business.datasource.domain.DataSource dataSource,
                        cn.superhuang.data.scalpel.business.model.domain.DataModel model,
                        java.util.List<cn.superhuang.data.scalpel.business.model.domain.DataModelField> fields,
                        TableMetadata metadata
                ) {
                    return inspect(dataSource, model, fields);
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
                    if (table.table().equals("source_view")) {
                        return new TableMetadata(
                                new TableSummary(table, "VIEW", "来源视图"),
                                List.of(new ColumnMetadata(
                                        "order_id", 1, Types.BIGINT, "int8", LogicalType.INTEGER,
                                        null, null, null, false, null, false, false, "订单主键"
                                )),
                                new PrimaryKeyMetadata(null, List.of()),
                                List.of()
                        );
                    }
                    if (table.table().equals("binary_source")) {
                        return new TableMetadata(
                                new TableSummary(table, "TABLE", "二进制字段表"),
                                List.of(new ColumnMetadata(
                                        "payload", 1, Types.BINARY, "bytea", LogicalType.BINARY,
                                        null, null, null, true, null, false, false, null
                                )),
                                new PrimaryKeyMetadata(null, List.of()),
                                List.of()
                        );
                    }
                    if (table.table().equals("problem_columns")) {
                        return new TableMetadata(
                                new TableSummary(table, "TABLE", "问题字段表"),
                                List.of(
                                        new ColumnMetadata(
                                                "Order_ID", 1, Types.BIGINT, "int8", LogicalType.INTEGER,
                                                null, null, null, false, "1", false, false, null
                                        ),
                                        new ColumnMetadata(
                                                "order_id", 2, Types.BIGINT, "int8", LogicalType.INTEGER,
                                                null, null, null, false, null, false, false, null
                                        ),
                                        new ColumnMetadata(
                                                "bad-name", 3, Types.VARCHAR, "varchar", LogicalType.STRING,
                                                20, null, null, true, null, false, false, null
                                        ),
                                        new ColumnMetadata(
                                                "event_time", 4, Types.TIME, "time", LogicalType.TIME,
                                                null, null, null, true, null, false, false, null
                                        )
                                ),
                                new PrimaryKeyMetadata(null, List.of()),
                                List.of(new IndexMetadata("idx_problem", false, List.of("order_id")))
                        );
                    }
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
                    if (table.table().equals("geometry_source")) {
                        return new TableMetadata(
                                new TableSummary(table, "TABLE", "PostGIS 空间表"),
                                List.of(new ColumnMetadata(
                                        "shape", 1, Types.OTHER, "geometry", LogicalType.OTHER,
                                        null, null, null, true, null, false, false, "行政区",
                                        new SpatialColumnMetadata(
                                                "MULTIPOLYGON", 990001, "EPSG", 4490,
                                                CoordinateDimension.XY, true, true
                                        )
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
                    assertNoManagementTransaction();
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
                    CREATE_CALLS.incrementAndGet();
                    READY_MODEL_IDS.add(model.getId());
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
                    LAST_QUERY.set(query);
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
