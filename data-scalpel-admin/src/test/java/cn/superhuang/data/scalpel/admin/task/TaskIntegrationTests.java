package cn.superhuang.data.scalpel.admin.task;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.directory.domain.Directory;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.repository.DirectoryRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunExecutionMode;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunTriggerType;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.repository.CanvasTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskInputRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskScheduleRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskCanvasModelReferenceRepository;
import cn.superhuang.data.scalpel.business.task.service.LocalSqlDefinitionInspection;
import cn.superhuang.data.scalpel.business.task.service.LocalSqlDefinitionInspectionPort;
import cn.superhuang.data.scalpel.business.task.service.LocalSqlDefinitionInspectionRequest;
import cn.superhuang.data.scalpel.business.task.service.TaskQuartzScheduler;
import cn.superhuang.data.scalpel.business.task.service.TaskRunService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
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
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Import(TaskIntegrationTests.TaskInspectionTestConfiguration.class)
class TaskIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private DataTaskRepository taskRepository;

    @Autowired
    private LocalSqlTaskDefinitionRepository definitionRepository;

    @Autowired
    private CanvasTaskDefinitionRepository canvasDefinitionRepository;

    @Autowired
    private TaskCanvasModelReferenceRepository canvasModelReferenceRepository;

    @Autowired
    private LocalSqlTaskInputRepository inputRepository;

    @Autowired
    private TaskRunRepository runRepository;

    @Autowired
    private TaskScheduleRepository scheduleRepository;

    @Autowired
    private TaskRunService taskRunService;

    @Autowired
    private TaskQuartzScheduler quartzScheduler;

    @Autowired
    private Scheduler scheduler;

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
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
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
    void managesLocalSqlDefinitionLifecycleAndProtectsItsReferences() throws Exception {
        Fixture fixture = fixture();
        String taskId = JsonPath.read(mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"每日金额汇总","directoryId":"%s","type":"LOCAL_SQL"}
                                """.formatted(fixture.taskDirectoryId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.type").value("LOCAL_SQL"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sql":"WITH selected AS (SELECT amount FROM source_amount) SELECT amount FROM selected;",
                                  "inputModelIds":["%s"],
                                  "outputModelId":"%s",
                                  "writeMode":"APPEND",
                                  "timeoutSeconds":300
                                }
                                """.formatted(fixture.inputModelId(), fixture.outputModelId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.sql").value("WITH selected AS (SELECT amount FROM source_amount) SELECT amount FROM selected"));

        mockMvc.perform(get("/api/v1/tasks/{id}/definition", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputs[0].modelId").value(fixture.inputModelId()))
                .andExpect(jsonPath("$.output.modelId").value(fixture.outputModelId()));
        mockMvc.perform(get("/api/v1/tasks/{id}/model-relations", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.definitionVersion").value(1))
                .andExpect(jsonPath("$.models.length()").value(2))
                .andExpect(jsonPath("$.models[0].modelId").value(fixture.inputModelId()))
                .andExpect(jsonPath("$.models[0].roles[0]").value("INPUT"))
                .andExpect(jsonPath("$.models[0].locations[0].referenceType").value("LOCAL_SQL_INPUT"))
                .andExpect(jsonPath("$.models[0].locations[0].ordinal").value(1))
                .andExpect(jsonPath("$.models[1].modelId").value(fixture.outputModelId()))
                .andExpect(jsonPath("$.models[1].roles[0]").value("OUTPUT"))
                .andExpect(jsonPath("$.models[1].locations[0].referenceType").value("LOCAL_SQL_OUTPUT"));
        mockMvc.perform(get("/api/v1/models/{id}/related-tasks", fixture.inputModelId())
                        .param("role", "INPUT")
                        .param("search", "name:*\"每日\"* AND type:\"LOCAL_SQL\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].taskId").value(taskId))
                .andExpect(jsonPath("$.content[0].roles[0]").value("INPUT"));
        mockMvc.perform(get("/api/v1/models/{id}/related-tasks", fixture.inputModelId())
                        .param("search", "status:\"PUBLISHED\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/models/{id}/related-tasks", fixture.inputModelId())
                        .param("role", "OUTPUT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/models/{id}/related-tasks", fixture.outputModelId())
                        .param("role", "OUTPUT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].taskId").value(taskId))
                .andExpect(jsonPath("$.content[0].roles[0]").value("OUTPUT"));
        mockMvc.perform(get("/api/v1/tasks/{id}/canvas-definition", taskId))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sql":"SELECT amount FROM source_amount; DELETE FROM target_amount",
                                  "inputModelIds":["%s"], "outputModelId":"%s",
                                  "writeMode":"APPEND", "timeoutSeconds":300
                                }
                                """.formatted(fixture.inputModelId(), fixture.outputModelId())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/publish", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.definitionConfigured").value(true))
                .andExpect(jsonPath("$.outputModelId").value(fixture.outputModelId()))
                .andExpect(jsonPath("$.outputModelName").value("目标金额"));
        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sql":"SELECT amount FROM source_amount", "inputModelIds":["%s"],
                                "outputModelId":"%s", "writeMode":"APPEND", "timeoutSeconds":300}
                                """.formatted(fixture.inputModelId(), fixture.outputModelId())))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/run", taskId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.definitionVersion").value(1))
                .andExpect(jsonPath("$.triggerType").value("MANUAL"))
                .andExpect(jsonPath("$.executionMode").value("REAL"));
        mockMvc.perform(get("/api/v1/tasks/{id}/runs", taskId)
                        .param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/disable", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
        mockMvc.perform(post("/api/v1/tasks/{id}/actions/enable", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.definitionConfigured").value(true))
                .andExpect(jsonPath("$.outputModelId").value(fixture.outputModelId()))
                .andExpect(jsonPath("$.outputModelName").value("目标金额"));
        mockMvc.perform(post("/api/v1/tasks/{id}/actions/disable", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
        mockMvc.perform(post("/api/v1/models/{id}/actions/disable", fixture.outputModelId()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/models/{id}/actions/delete", fixture.outputModelId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("模型已被本地 SQL 任务引用，不能删除"));
        mockMvc.perform(post("/api/v1/directories/{id}/actions/delete", fixture.taskDirectoryId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("目录包含业务数据，不能删除"));

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/delete", taskId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("任务已有运行记录，不能删除"));
    }

    @Test
    void disablesAndDeletesPublishedLocalSqlTaskWhenItsDefinitionIsMissing() throws Exception {
        Fixture fixture = fixture();
        String taskId = createConfiguredTask(fixture, "missing_local_sql_definition");
        UUID taskUuid = UUID.fromString(taskId);

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/publish", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        definitionRepository.delete(definitionRepository.findByTaskId(taskUuid).orElseThrow());
        definitionRepository.flush();

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/disable", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.definitionConfigured").value(false));
        mockMvc.perform(post("/api/v1/tasks/{id}/actions/delete", taskId))
                .andExpect(status().isNoContent());

        assertThat(taskRepository.findById(taskUuid)).isEmpty();
    }

    @Test
    void managesSparkCanvasDefinitionWithoutExecutionOrScheduling() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"缺少类型"}
                                """))
                .andExpect(status().isBadRequest());

        String taskId = JsonPath.read(mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"客户编排","type":"SPARK_CANVAS"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SPARK_CANVAS"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.definitionConfigured").value(false))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"客户编排已改名","type":"LOCAL_SQL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("客户编排已改名"))
                .andExpect(jsonPath("$.type").value("SPARK_CANVAS"));

        mockMvc.perform(get("/api/v1/tasks/{id}/canvas-definition", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.definition.schemaVersion").value(1))
                .andExpect(jsonPath("$.definition.schemaMinorVersion").value(6))
                .andExpect(jsonPath("$.definition.nodes").isEmpty())
                .andExpect(jsonPath("$.definition.edges").isEmpty());
        mockMvc.perform(get("/api/v1/tasks/{id}/model-relations", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.definitionVersion").doesNotExist())
                .andExpect(jsonPath("$.models").isEmpty());

        String nodeId = UUID.randomUUID().toString();
        String definition = canvasDefinitionJson(nodeId, "客户输入");
        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-canvas-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(definition))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.definition.schemaVersion").value(1))
                .andExpect(jsonPath("$.definition.schemaMinorVersion").value(6))
                .andExpect(jsonPath("$.definition.nodes[0].type").value("JDBC_INPUT"))
                .andExpect(jsonPath("$.definition.nodes[0].name").value("客户输入"));
        var persistedCanvas = canvasDefinitionRepository.findByTaskId(UUID.fromString(taskId)).orElseThrow();
        assertThat(persistedCanvas.getSchemaVersion()).isEqualTo(1);
        assertThat(persistedCanvas.getSchemaMinorVersion()).isEqualTo(6);
        assertThat(persistedCanvas.getDefinitionJson()).contains("\"schemaMinorVersion\":6");
        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-canvas-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(definition))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-canvas-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(canvasDefinitionJson(nodeId, "客户数据输入")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(get("/api/v1/tasks/{id}/canvas-definition", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.definition.nodes[0].id").value(nodeId))
                .andExpect(jsonPath("$.definition.nodes[0].name").value("客户数据输入"));

        mockMvc.perform(get("/api/v1/tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitionConfigured").value(true))
                .andExpect(jsonPath("$.definitionVersion").value(2));
        mockMvc.perform(get("/api/v1/tasks/{id}/definition", taskId))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/tasks/{id}/actions/publish", taskId))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/tasks/{id}/actions/run", taskId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("只有已发布任务可以运行"));
        String canvasScheduleId = JsonPath.read(mockMvc.perform(post("/api/v1/tasks/{id}/schedules", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson("Canvas 定时计划", "0 0 2 * * ?", "Asia/Shanghai", "ALLOW")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.overlapPolicy").value("ALLOW"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        UUID taskUuid = UUID.fromString(taskId);
        mockMvc.perform(post("/api/v1/task-schedules/{id}/actions/enable", canvasScheduleId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("只有已发布任务可以启用运行计划"));

        assertThat(canvasDefinitionRepository.findByTaskId(taskUuid)).isPresent();
        mockMvc.perform(post("/api/v1/tasks/{id}/actions/delete", taskId))
                .andExpect(status().isNoContent());
        assertThat(canvasDefinitionRepository.findByTaskId(taskUuid)).isEmpty();
        assertThat(taskRepository.findById(taskUuid)).isEmpty();
    }

    @Test
    void replacesAndDeletesCanvasModelReferenceProjection() throws Exception {
        Fixture fixture = fixture();
        String taskId = JsonPath.read(mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"模型引用编排","type":"SPARK_CANVAS"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        UUID inputModelId = UUID.fromString(fixture.inputModelId());
        UUID outputModelId = UUID.fromString(fixture.outputModelId());

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-canvas-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modelCanvasDefinitionJson(inputModelId, outputModelId)))
                .andExpect(status().isOk());

        var references = canvasModelReferenceRepository.findAllByTaskIdOrderByNodeId(UUID.fromString(taskId));
        assertThat(references).hasSize(2);
        assertThat(references).extracting(reference -> reference.getModelId())
                .containsExactlyInAnyOrder(inputModelId, outputModelId);
        mockMvc.perform(get("/api/v1/tasks/{id}/model-relations", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models.length()").value(2))
                .andExpect(jsonPath("$.models[0].modelId").value(inputModelId.toString()))
                .andExpect(jsonPath("$.models[0].locations[0].nodeName").value("模型输入"))
                .andExpect(jsonPath("$.models[1].modelId").value(outputModelId.toString()))
                .andExpect(jsonPath("$.models[1].locations[0].nodeName").value("模型输出"));
        mockMvc.perform(get("/api/v1/models/{id}/related-tasks", inputModelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].taskId").value(taskId))
                .andExpect(jsonPath("$.content[0].locations[0].nodeName").value("模型输入"));

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-canvas-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modelCanvasDefinitionJson(inputModelId, null)))
                .andExpect(status().isOk());
        assertThat(canvasModelReferenceRepository.findAllByTaskIdOrderByNodeId(UUID.fromString(taskId)))
                .singleElement()
                .satisfies(reference -> assertThat(reference.getModelId()).isEqualTo(inputModelId));

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/delete", taskId))
                .andExpect(status().isNoContent());
        assertThat(canvasModelReferenceRepository.findAllByTaskIdOrderByNodeId(UUID.fromString(taskId))).isEmpty();
    }

    @Test
    void aggregatesMultipleCanvasLocationsAndRolesByTaskAndModel() throws Exception {
        Fixture fixture = fixture();
        UUID modelId = UUID.fromString(fixture.inputModelId());
        String taskId = JsonPath.read(mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"模型多位置编排","type":"SPARK_CANVAS"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-canvas-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(multiReferenceModelCanvasDefinitionJson(modelId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tasks/{id}/model-relations", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models.length()").value(1))
                .andExpect(jsonPath("$.models[0].modelId").value(modelId.toString()))
                .andExpect(jsonPath("$.models[0].roles.length()").value(2))
                .andExpect(jsonPath("$.models[0].roles[0]").value("INPUT"))
                .andExpect(jsonPath("$.models[0].roles[1]").value("OUTPUT"))
                .andExpect(jsonPath("$.models[0].locations.length()").value(3))
                .andExpect(jsonPath("$.models[0].locations[0].nodeName").value("输入 A"))
                .andExpect(jsonPath("$.models[0].locations[1].nodeName").value("输入 B"))
                .andExpect(jsonPath("$.models[0].locations[2].nodeName").value("模型输出"));

        mockMvc.perform(get("/api/v1/models/{id}/related-tasks", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].taskId").value(taskId))
                .andExpect(jsonPath("$.content[0].roles.length()").value(2))
                .andExpect(jsonPath("$.content[0].locations.length()").value(3));
    }

    @Test
    void rejectsCanvasDefinitionsThatCannotBeSafelyReloaded() throws Exception {
        String taskId = JsonPath.read(mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"非法编排","type":"SPARK_CANVAS"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        String nodeId = UUID.randomUUID().toString();
        String edgeId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-canvas-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"definition":{"schemaVersion":1,"schemaMinorVersion":1,"nodes":[
                                  {"id":"%s","type":"JDBC_INPUT","name":"输入一","layout":{"x":0,"y":0,"width":240,"height":120},"configuration":{"dataSourceId":"","tableName":""}},
                                  {"id":"%s","type":"JDBC_INPUT","name":"输入二","layout":{"x":0,"y":160,"width":240,"height":120},"configuration":{"dataSourceId":"","tableName":""}}
                                ],"edges":[]}}
                                """.formatted(nodeId, nodeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("节点 ID " + nodeId + " 重复"));

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-canvas-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"definition":{"schemaVersion":1,"schemaMinorVersion":1,"nodes":[
                                  {"id":"%s","type":"JDBC_INPUT","name":"输入","layout":{"x":0,"y":0,"width":240,"height":120},"configuration":{"dataSourceId":"","tableName":""}}
                                ],"edges":[{"id":"%s","sourceNodeId":"%s","targetNodeId":"%s"}]}}
                                """.formatted(nodeId, edgeId, nodeId, UUID.randomUUID())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-canvas-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"definition":{"schemaVersion":1,"schemaMinorVersion":1,"nodes":[
                                  {"id":"%s","type":"JDBC_INPUT","name":"输入","layout":{"x":0,"y":0,"width":100,"height":120},"configuration":{"dataSourceId":"","tableName":""}}
                                ],"edges":[]}}
                """.formatted(nodeId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-canvas-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"definition":{"schemaVersion":2,"schemaMinorVersion":0,"nodes":[],"edges":[]}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Canvas schemaVersion 仅支持 1"));

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-canvas-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"definition":{"schemaVersion":1,"schemaMinorVersion":7,"nodes":[],"edges":[]}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Canvas schemaMinorVersion 仅支持 0 到 6"));

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-canvas-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"definition":{"schemaVersion":1,"nodes":[
                                  {"id":"%s","type":"MODEL_INPUT","name":"模型输入","layout":{"x":0,"y":0,"width":240,"height":120},"configuration":{"modelId":""}}
                                ],"edges":[]}}
                                """.formatted(nodeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("MODEL_INPUT 和 MODEL_OUTPUT 从 Canvas 1.1 开始支持"));

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-canvas-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"definition":{"schemaVersion":1,"schemaMinorVersion":1,"nodes":[
                                  {"id":"%s","type":"FILTER","name":"未知节点","layout":{"x":0,"y":0,"width":240,"height":120},"configuration":{}}
                                ],"edges":[]}}
                                """.formatted(nodeId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-canvas-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"definition":{"schemaVersion":1,"schemaMinorVersion":1,"nodes":[
                                  {"id":"%s","type":"JDBC_INPUT","name":"输入","layout":{"x":0,"y":0,"width":240,"height":120},"configuration":{"dataSourceId":"","tableName":""}}
                                ],"edges":[
                                  {"id":"%s","sourceNodeId":"%s","targetNodeId":"%s"},
                                  {"id":"%s","sourceNodeId":"%s","targetNodeId":"%s"}
                                ]}}
                                """.formatted(nodeId, edgeId, nodeId, nodeId, edgeId, nodeId, nodeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("连线 ID " + edgeId + " 重复"));

        assertThat(canvasDefinitionRepository.findByTaskId(UUID.fromString(taskId))).isEmpty();
    }

    @Test
    void rejectsCanvasDefinitionLargerThanFiveMebibytes() throws Exception {
        String taskId = JsonPath.read(mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"超大编排","type":"SPARK_CANVAS"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        String oversizedTableName = "a".repeat(5 * 1024 * 1024);

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-canvas-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"definition":{"schemaVersion":1,"schemaMinorVersion":1,"nodes":[
                                  {"id":"%s","type":"JDBC_INPUT","name":"输入","layout":{"x":0,"y":0,"width":240,"height":120},"configuration":{"dataSourceId":"","tableName":"%s"}}
                                ],"edges":[]}}
                                """.formatted(UUID.randomUUID(), oversizedTableName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Canvas 定义不能超过 5 MiB"));

        assertThat(canvasDefinitionRepository.findByTaskId(UUID.fromString(taskId))).isEmpty();
    }

    @Test
    void managesScheduleLifecycleAndSynchronizesQuartz() throws Exception {
        Fixture fixture = fixture();
        String taskId = createConfiguredTask(fixture, "schedule_lifecycle");

        mockMvc.perform(post("/api/v1/tasks/{id}/schedules", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson("凌晨计划", "0 2 * * *", "Asia/Shanghai", "FORBID")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Quartz Cron 表达式无效"));
        mockMvc.perform(post("/api/v1/tasks/{id}/schedules", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson("凌晨计划", "0 0 2 * * ?", "Mars/Olympus", "FORBID")))
                .andExpect(status().isBadRequest());

        String scheduleId = JsonPath.read(mockMvc.perform(post("/api/v1/tasks/{id}/schedules", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson("凌晨计划", "0 0 2 * * ?", "Asia/Shanghai", "FORBID")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.nextFireAt").doesNotExist())
                .andReturn().getResponse().getContentAsString(), "$.id");
        UUID scheduleUuid = UUID.fromString(scheduleId);
        assertThat(quartzScheduler.nextFireAt(scheduleUuid)).isNotNull();

        mockMvc.perform(post("/api/v1/tasks/{id}/schedules", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson("凌晨计划", "0 0 3 * * ?", "Asia/Shanghai", "FORBID")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/task-schedules/{id}/actions/enable", scheduleId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("只有已发布任务可以启用运行计划"));

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/publish", taskId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/task-schedules/{id}/actions/enable", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.nextFireAt").isNotEmpty());
        scheduler.triggerJob(TaskQuartzScheduler.jobKey(scheduleUuid));
        TaskRun quartzRun = awaitScheduledRun(scheduleUuid);
        assertThat(quartzRun.getTriggerType()).isEqualTo(TaskRunTriggerType.SCHEDULED);
        assertThat(quartzRun.getExecutionMode()).isEqualTo(TaskRunExecutionMode.SIMULATED);
        assertThat(quartzRun.getStatus()).isEqualTo(TaskRunStatus.SUCCESS);

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/disable", taskId))
                .andExpect(status().isOk());
        assertThat(quartzScheduler.nextFireAt(scheduleUuid)).isNotNull();
        mockMvc.perform(get("/api/v1/tasks/{id}/schedules", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ENABLED"))
                .andExpect(jsonPath("$[0].nextFireAt").doesNotExist());

        mockMvc.perform(post("/api/v1/tasks/{id}/actions/enable", taskId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tasks/{id}/schedules", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nextFireAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/task-schedules/{id}/actions/delete", scheduleId))
                .andExpect(status().isNoContent());
        assertThat(scheduleRepository.findById(scheduleUuid)).isEmpty();
        assertThat(quartzScheduler.nextFireAt(scheduleUuid)).isNull();
    }

    @Test
    void rejectsSchedulesForStreamingCanvasTasks() throws Exception {
        DataTask task = taskRepository.saveAndFlush(DataTask.create(
                "streaming_schedule_rejected",
                null,
                TaskType.SPARK_STREAMING_CANVAS,
                null,
                UUID.randomUUID()
        ));

        mockMvc.perform(post("/api/v1/tasks/{id}/schedules", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson("不应创建", "0 0 2 * * ?", "Asia/Shanghai", "ALLOW")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Spark 实时任务持续运行，不支持定时计划"));
    }

    @Test
    void createsIdempotentSimulatedScheduledRunsAndAppliesOverlapPolicy() throws Exception {
        Fixture fixture = fixture();
        String taskId = createConfiguredTask(fixture, "scheduled_runs");
        mockMvc.perform(post("/api/v1/tasks/{id}/actions/publish", taskId)).andExpect(status().isOk());
        String scheduleId = JsonPath.read(mockMvc.perform(post("/api/v1/tasks/{id}/schedules", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson("每小时计划", "0 0 * * * ?", "Asia/Shanghai", "FORBID")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(post("/api/v1/task-schedules/{id}/actions/enable", scheduleId)).andExpect(status().isOk());

        UUID taskUuid = UUID.fromString(taskId);
        UUID scheduleUuid = UUID.fromString(scheduleId);
        Instant firstFireAt = Instant.parse("2027-01-01T00:00:00Z");
        taskRunService.runScheduled(scheduleUuid, firstFireAt);
        taskRunService.runScheduled(scheduleUuid, firstFireAt);

        TaskRun success = runRepository.findByScheduleIdAndScheduledFireAt(scheduleUuid, firstFireAt).orElseThrow();
        assertThat(success.getTaskId()).isEqualTo(taskUuid);
        assertThat(success.getTriggerType()).isEqualTo(TaskRunTriggerType.SCHEDULED);
        assertThat(success.getExecutionMode()).isEqualTo(TaskRunExecutionMode.SIMULATED);
        assertThat(success.getStatus()).isEqualTo(TaskRunStatus.SUCCESS);
        assertThat(success.getAffectedRows()).isNull();
        assertThat(success.getMessage()).isEqualTo("定时触发成功（模拟执行，未访问数据源）");
        assertThat(success.getQueuedAt()).isEqualTo(success.getStartedAt()).isEqualTo(success.getEndedAt());
        assertThat(runRepository.findAll().stream()
                .filter(run -> scheduleUuid.equals(run.getScheduleId()) && firstFireAt.equals(run.getScheduledFireAt())))
                .hasSize(1);

        runRepository.saveAndFlush(TaskRun.queue(taskUuid, 1, "{}"));
        Instant secondFireAt = firstFireAt.plusSeconds(3600);
        taskRunService.runScheduled(scheduleUuid, secondFireAt);
        TaskRun skipped = runRepository.findByScheduleIdAndScheduledFireAt(scheduleUuid, secondFireAt).orElseThrow();
        assertThat(skipped.getStatus()).isEqualTo(TaskRunStatus.SKIPPED);
        assertThat(skipped.getExecutionMode()).isEqualTo(TaskRunExecutionMode.SIMULATED);

        mockMvc.perform(post("/api/v1/task-schedules/{id}/actions/update", scheduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson("每小时计划", "0 0 * * * ?", "Asia/Shanghai", "ALLOW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overlapPolicy").value("ALLOW"));
        Instant thirdFireAt = secondFireAt.plusSeconds(3600);
        taskRunService.runScheduled(scheduleUuid, thirdFireAt);
        assertThat(runRepository.findByScheduleIdAndScheduledFireAt(scheduleUuid, thirdFireAt).orElseThrow().getStatus())
                .isEqualTo(TaskRunStatus.SUCCESS);
    }

    private String createConfiguredTask(Fixture fixture, String name) throws Exception {
        String taskId = JsonPath.read(mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","directoryId":"%s","type":"LOCAL_SQL"}
                                """.formatted(name, fixture.taskDirectoryId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(post("/api/v1/tasks/{id}/actions/update-definition", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sql":"SELECT amount FROM source_amount",
                                  "inputModelIds":["%s"],
                                  "outputModelId":"%s",
                                  "writeMode":"APPEND",
                                  "timeoutSeconds":300
                                }
                                """.formatted(fixture.inputModelId(), fixture.outputModelId())))
                .andExpect(status().isOk());
        return taskId;
    }

    private static String scheduleJson(
            String name,
            String cronExpression,
            String zoneId,
            String overlapPolicy
    ) {
        return """
                {
                  "name":"%s",
                  "cronExpression":"%s",
                  "zoneId":"%s",
                  "misfirePolicy":"FIRE_ONCE_NOW",
                  "overlapPolicy":"%s"
                }
                """.formatted(name, cronExpression, zoneId, overlapPolicy);
    }

    private static String canvasDefinitionJson(String nodeId, String nodeName) {
        return """
                {"definition":{"schemaVersion":1,"nodes":[
                  {"id":"%s","type":"JDBC_INPUT","name":"%s","layout":{"x":80,"y":80,"width":240,"height":120},"configuration":{"dataSourceId":"","tableName":""}}
                ],"edges":[]}}
                """.formatted(nodeId, nodeName);
    }

    private static String modelCanvasDefinitionJson(UUID inputModelId, UUID outputModelId) {
        String inputNodeId = UUID.randomUUID().toString();
        if (outputModelId == null) {
            return """
                    {"definition":{"schemaVersion":1,"schemaMinorVersion":1,"nodes":[
                      {"id":"%s","type":"MODEL_INPUT","name":"模型输入",
                       "layout":{"x":80,"y":80,"width":240,"height":120},
                       "configuration":{"modelId":"%s"}}
                    ],"edges":[]}}
                    """.formatted(inputNodeId, inputModelId);
        }
        String outputNodeId = UUID.randomUUID().toString();
        return """
                {"definition":{"schemaVersion":1,"schemaMinorVersion":1,"nodes":[
                  {"id":"%s","type":"MODEL_INPUT","name":"模型输入",
                   "layout":{"x":80,"y":80,"width":240,"height":120},
                   "configuration":{"modelId":"%s"}},
                  {"id":"%s","type":"MODEL_OUTPUT","name":"模型输出",
                   "layout":{"x":400,"y":80,"width":240,"height":120},
                   "configuration":{"sourceTableName":"source_model","targetModelId":"%s",
                   "writeMode":"APPEND","columnMappingMode":"BY_NAME","columnMappings":[]}}
                ],"edges":[{"id":"%s","sourceNodeId":"%s","targetNodeId":"%s"}]}}
                """.formatted(
                inputNodeId, inputModelId, outputNodeId, outputModelId,
                UUID.randomUUID(), inputNodeId, outputNodeId
        );
    }

    private static String multiReferenceModelCanvasDefinitionJson(UUID modelId) {
        return """
                {"definition":{"schemaVersion":1,"schemaMinorVersion":1,"nodes":[
                  {"id":"%s","type":"MODEL_INPUT","name":"输入 B",
                   "layout":{"x":80,"y":80,"width":240,"height":120},
                   "configuration":{"modelId":"%s"}},
                  {"id":"%s","type":"MODEL_INPUT","name":"输入 A",
                   "layout":{"x":80,"y":240,"width":240,"height":120},
                   "configuration":{"modelId":"%s"}},
                  {"id":"%s","type":"MODEL_OUTPUT","name":"模型输出",
                   "layout":{"x":400,"y":80,"width":240,"height":120},
                   "configuration":{"sourceTableName":"source_model","targetModelId":"%s",
                   "writeMode":"APPEND","columnMappingMode":"BY_NAME","columnMappings":[]}}
                ],"edges":[]}}
                """.formatted(
                UUID.randomUUID(), modelId,
                UUID.randomUUID(), modelId,
                UUID.randomUUID(), modelId
        );
    }

    private TaskRun awaitScheduledRun(UUID scheduleId) throws InterruptedException {
        for (int attempt = 0; attempt < 40; attempt++) {
            TaskRun run = runRepository.findAll().stream()
                    .filter(candidate -> scheduleId.equals(candidate.getScheduleId()))
                    .findFirst()
                    .orElse(null);
            if (run != null) {
                return run;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Quartz did not create a scheduled task run in time");
    }

    private Fixture fixture() {
        DataSource source = dataSourceRepository.saveAndFlush(DataSource.create(
                "task_storage", "任务存储", null, Set.of(DataSourcePurpose.STORAGE), DataSourceType.POSTGRESQL, true, null,
                DataSourceConnection.jdbc("127.0.0.1", 1, "task_test", "public", "task", "secret", Map.of())
        ));
        Directory directory = directoryRepository.saveAndFlush(Directory.create(DirectoryScope.TASK, null, "任务目录", 0, null));
        DataModel input = modelRepository.saveAndFlush(DataModel.create(
                "source_amount", "源金额", null, source.getId(), null, null, "source_amount", PhysicalTableMode.MANAGED, null
        ));
        DataModel output = modelRepository.saveAndFlush(DataModel.create(
                "target_amount", "目标金额", null, source.getId(), null, null, "target_amount", PhysicalTableMode.MANAGED, null
        ));
        fieldRepository.saveAllAndFlush(List.of(
                DataModelField.create(input.getId(), "amount", "金额", PlatformDataType.DECIMAL, null, 18, 2, true, false, 0, null),
                DataModelField.create(output.getId(), "amount", "金额", PlatformDataType.DECIMAL, null, 18, 2, true, false, 0, null)
        ));
        input.publish();
        output.publish();
        modelRepository.saveAllAndFlush(List.of(input, output));
        return new Fixture(directory.getId().toString(), input.getId().toString(), output.getId().toString());
    }

    private void clearData() {
        quartzScheduler.deleteOrphans(Set.of());
        runRepository.deleteAll();
        scheduleRepository.deleteAll();
        inputRepository.deleteAll();
        definitionRepository.deleteAll();
        canvasDefinitionRepository.deleteAll();
        canvasModelReferenceRepository.deleteAll();
        taskRepository.deleteAll();
        fieldRepository.deleteAll();
        modelRepository.deleteAll();
        dataSourceRepository.deleteAll();
        directoryRepository.deleteAll();
    }

    private record Fixture(String taskDirectoryId, String inputModelId, String outputModelId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TaskInspectionTestConfiguration {

        @Bean
        @Primary
        LocalSqlDefinitionInspectionPort localSqlDefinitionInspectionPort() {
            return request -> new LocalSqlDefinitionInspection(List.of(), List.of(), List.of("amount"), "INSERT INTO target_amount (amount) SELECT amount");
        }
    }
}
