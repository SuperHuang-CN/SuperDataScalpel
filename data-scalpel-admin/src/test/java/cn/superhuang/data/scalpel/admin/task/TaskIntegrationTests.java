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
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskInputRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.service.LocalSqlDefinitionInspection;
import cn.superhuang.data.scalpel.business.task.service.LocalSqlDefinitionInspectionPort;
import cn.superhuang.data.scalpel.business.task.service.LocalSqlDefinitionInspectionRequest;
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
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
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
    private LocalSqlTaskInputRepository inputRepository;

    @Autowired
    private TaskRunRepository runRepository;

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
                                {"code":"daily_amount","name":"每日金额汇总","directoryId":"%s"}
                                """.formatted(fixture.taskDirectoryId())))
                .andExpect(status().isCreated())
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
                .andExpect(jsonPath("$.definitionVersion").value(1));
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
        runRepository.deleteAll();
        inputRepository.deleteAll();
        definitionRepository.deleteAll();
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
