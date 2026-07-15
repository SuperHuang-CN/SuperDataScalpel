package cn.superhuang.data.scalpel.admin.directory;

import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.directory.repository.DirectoryRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
class DirectoryIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;

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
    void managesScopedTreeAndProtectsDirectoriesReferencedByDataSources() throws Exception {
        String rootId = createDirectory("""
                { "scope": "DATA_SOURCE", "name": "业务系统", "sortOrder": 10, "description": "业务数据连接" }
                """);
        String childId = createDirectory("""
                { "scope": "DATA_SOURCE", "parentId": "%s", "name": "综合业务库", "sortOrder": 10 }
                """.formatted(rootId));

        mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "directory_sample",
                                  "name": "目录示例连接",
                                  "directoryId": "%s",
                                  "purposes": ["SOURCE"],
                                  "type": "POSTGRESQL",
                                  "connection": {
                                    "kind": "JDBC",
                                    "host": "localhost",
                                    "port": 5432,
                                    "databaseName": "sample",
                                    "username": "datascalpel"
                                  }
                                }
                                """.formatted(childId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.directoryId").value(childId));

        mockMvc.perform(get("/api/v1/directories").param("scope", "DATA_SOURCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("业务系统"))
                .andExpect(jsonPath("$[0].directResourceCount").value(0))
                .andExpect(jsonPath("$[0].resourceCount").value(1))
                .andExpect(jsonPath("$[0].children[0].name").value("综合业务库"))
                .andExpect(jsonPath("$[0].children[0].directResourceCount").value(1));

        mockMvc.perform(get("/api/v1/data-sources")
                        .param("search", "(directoryId:\"%s\" OR directoryId:\"%s\")".formatted(rootId, childId))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].directoryId").value(childId));

        mockMvc.perform(post("/api/v1/directories/{id}/actions/delete", rootId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("目录包含子目录，不能删除"));

        mockMvc.perform(post("/api/v1/directories/{id}/actions/delete", childId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("目录包含业务数据，不能删除"));

        mockMvc.perform(post("/api/v1/directories/{id}/actions/update", childId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "parentId": "%s", "name": "综合业务库（更新）", "sortOrder": 20, "description": "更新后的说明" }
                                """.formatted(rootId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("综合业务库（更新）"));
    }

    @Test
    void rejectsCrossScopeAssignmentAndCircularDirectories() throws Exception {
        String dataSourceDirectoryId = createDirectory("""
                { "scope": "DATA_SOURCE", "name": "数据源目录", "sortOrder": 0 }
                """);
        String modelDirectoryId = createDirectory("""
                { "scope": "MODEL", "name": "模型目录", "sortOrder": 0 }
                """);

        mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "wrong_directory",
                                  "name": "错误目录",
                                  "directoryId": "%s",
                                  "purposes": ["SOURCE"],
                                  "type": "POSTGRESQL",
                                  "connection": {
                                    "kind": "JDBC",
                                    "host": "localhost", "port": 5432, "databaseName": "sample", "username": "datascalpel"
                                  }
                                }
                                """.formatted(modelDirectoryId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("目录类型不匹配"));

        mockMvc.perform(post("/api/v1/directories/{id}/actions/update", dataSourceDirectoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "parentId": "%s", "name": "数据源目录", "sortOrder": 0 }
                                """.formatted(dataSourceDirectoryId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("目录不能以自身为父目录"));
    }

    private String createDirectory(String request) throws Exception {
        String response = mockMvc.perform(post("/api/v1/directories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private void clearData() {
        dataSourceRepository.deleteAll();
        directoryRepository.deleteAll();
    }
}
