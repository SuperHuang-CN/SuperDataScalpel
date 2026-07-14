package cn.superhuang.data.scalpel.admin.model;

import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.directory.repository.DirectoryRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
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
    void managesMetadataFieldsAndLifecycleWithoutManagingPhysicalTables() throws Exception {
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
                                  "catalogName": "warehouse",
                                  "schemaName": "public",
                                  "physicalTableName": "Fact_Order",
                                  "description": "第一版只保存元数据"
                                }
                                """.formatted(directoryId, storageId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.model.code").value("order_fact"))
                .andExpect(jsonPath("$.model.physicalTableName").value("fact_order"))
                .andExpect(jsonPath("$.model.status").value("DRAFT"))
                .andExpect(jsonPath("$.physicalTableManaged").value(false))
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

        mockMvc.perform(post("/api/v1/models/{id}/actions/publish", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.physicalTableManaged").value(false));

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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("字符串字段必须指定长度：title"));
    }

    private String createDataSource(String code, String name, String purpose, boolean enabled) throws Exception {
        String response = mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s", "name": "%s", "purposes": ["%s"],
                                  "databaseType": "POSTGRESQL", "enabled": %s,
                                  "connection": {
                                    "host": "localhost", "port": 5432,
                                    "databaseName": "warehouse", "schemaName": "public", "username": "tester"
                                  }
                                }
                                """.formatted(code, name, purpose, enabled)))
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
                  "catalogName": "warehouse", "schemaName": "public", "physicalTableName": "%s"
                }
                """.formatted(code, code, storageId, physicalTableName);
    }

    private void clearData() {
        fieldRepository.deleteAll();
        modelRepository.deleteAll();
        dataSourceRepository.deleteAll();
        directoryRepository.deleteAll();
    }
}
