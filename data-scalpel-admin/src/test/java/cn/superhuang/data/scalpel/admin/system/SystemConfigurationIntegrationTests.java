package cn.superhuang.data.scalpel.admin.system;

import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfiguration;
import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfigurationValueType;
import cn.superhuang.data.scalpel.business.system.configuration.repository.SystemConfigurationRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
class SystemConfigurationIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private SystemConfigurationRepository repository;

    private MockMvc mockMvc;
    private String originalPlatformName;

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
        originalPlatformName = platformName().getConfigValue();
    }

    @AfterEach
    void restorePlatformName() {
        SystemConfiguration configuration = platformName();
        configuration.updateValue(originalPlatformName);
        repository.saveAndFlush(configuration);
    }

    @Test
    void initializesDeclaredConfigurationsAndSearchesWithTheCommonSearchEngine() throws Exception {
        mockMvc.perform(get("/api/v1/system/configurations")
                        .param("search", "configKey:\"platform.name\"")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "sortOrder,configKey"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].configKey").value("platform.name"))
                .andExpect(jsonPath("$.content[0].valueType").value("STRING"));
    }

    @Test
    void updatesOnlyTheConfigurationValueWithoutAuthentication() throws Exception {
        SystemConfiguration configuration = platformName();

        mockMvc.perform(post("/api/v1/system/configurations/{id}/actions/update", configuration.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configValue\":\"DataScalpel 测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configKey").value("platform.name"))
                .andExpect(jsonPath("$.configValue").value("DataScalpel 测试"));

        assertThat(platformName().getConfigValue()).isEqualTo("DataScalpel 测试");
    }

    @Test
    void rejectsInvalidTypedValuesAndUnknownConfigurations() throws Exception {
        SystemConfiguration integerConfiguration = repository.saveAndFlush(SystemConfiguration.create(
                "test.integer.configuration",
                "测试整数配置",
                "10",
                SystemConfigurationValueType.INTEGER,
                "仅用于验证类型校验。",
                999
        ));

        mockMvc.perform(post("/api/v1/system/configurations/{id}/actions/update", integerConfiguration.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configValue\":\"not-a-number\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/v1/system/configurations/{id}/actions/update", "00000000-0000-0000-0000-000000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configValue\":\"ignored\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        repository.delete(integerConfiguration);
    }

    private SystemConfiguration platformName() {
        return repository.findByConfigKey("platform.name").orElseThrow();
    }
}
