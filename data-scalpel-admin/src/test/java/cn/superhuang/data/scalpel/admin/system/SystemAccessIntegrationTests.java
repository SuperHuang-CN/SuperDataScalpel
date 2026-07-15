package cn.superhuang.data.scalpel.admin.system;

import cn.superhuang.data.scalpel.business.system.access.repository.SystemPermissionRepository;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemRoleRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
class SystemAccessIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private SystemPermissionRepository permissionRepository;

    @Autowired
    private SystemRoleRepository roleRepository;

    private MockMvc mockMvc;
    private MockMvc rawMockMvc;

    @BeforeEach
    void setUp() throws Exception {
        rawMockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        String accessToken = loginAsAdministrator(rawMockMvc);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .defaultRequest(get("/").header("Authorization", "Bearer " + accessToken))
                .apply(springSecurity())
                .build();
    }

    @Test
    void managesSingleRoleUsersAndEnforcesTheConfiguredPermissions() throws Exception {
        String roleResponse = mockMvc.perform(post("/api/v1/system/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"data_reader","name":"数据查看人员","description":"只能查看数据源。"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String roleId = JsonPath.read(roleResponse, "$.id");
        String dataSourceViewPermissionId = permissionRepository.findByCode("datasource.view").orElseThrow().getId().toString();

        mockMvc.perform(post("/api/v1/system/roles/{id}/actions/update-permissions", roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionIds":["%s"]}
                                """.formatted(dataSourceViewPermissionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionIds[0]").value(dataSourceViewPermissionId));

        String userResponse = mockMvc.perform(post("/api/v1/system/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"data.reader",
                                  "displayName":"数据查看员",
                                  "password":"reader123456",
                                  "roleId":"%s",
                                  "enabled":true
                                }
                                """.formatted(roleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleId").value(roleId))
                .andReturn().getResponse().getContentAsString();
        String userId = JsonPath.read(userResponse, "$.id");

        String userTokenResponse = rawMockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"data.reader\",\"password\":\"reader123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String userToken = JsonPath.read(userTokenResponse, "$.accessToken");

        rawMockMvc.perform(get("/api/v1/data-sources").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
        rawMockMvc.perform(get("/api/v1/data-source-types").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
        rawMockMvc.perform(get("/api/v1/system/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:datascalpel:problem:access-denied"))
                .andExpect(jsonPath("$.title").value("无权访问"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("当前账号无权访问该资源"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.instance").value("/api/v1/system/users"))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(post("/api/v1/system/roles/{id}/actions/delete", roleId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("角色已被用户使用，不能删除"));

        mockMvc.perform(post("/api/v1/system/users/{id}/actions/delete", userId))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/system/roles/{id}/actions/delete", roleId))
                .andExpect(status().isNoContent());
    }

    @Test
    void exposesCodeManagedPermissionsAndProtectsTheBuiltInAdministratorRole() throws Exception {
        String superAdminRoleId = roleRepository.findByCode("super_admin").orElseThrow().getId().toString();
        String permissionId = permissionRepository.findByCode("system.user.view").orElseThrow().getId().toString();

        mockMvc.perform(get("/api/v1/system/permissions")
                        .param("search", "code:*\"datasource\"*")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.content[0].active").value(true));

        mockMvc.perform(post("/api/v1/system/roles/{id}/actions/update-permissions", superAdminRoleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[\"" + permissionId + "\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("内置角色的权限由系统自动维护"));
    }
}
