package cn.superhuang.data.scalpel.admin.datasource;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(authorities = {"datasource.view", "model.view"})
class ModelSourceCandidatesIntegrationTests {
    @MockitoBean cn.superhuang.data.scalpel.business.task.service.TaskRunArtifactStorage artifactStorage;
    // This query test does not exercise the PostgreSQL-specific startup permission lock.
    @MockitoBean(name = "initializeSystemAccess") ApplicationRunner systemAccessInitializer;
    @Autowired WebApplicationContext context;
    @Autowired DataSourceRepository sources;
    @Autowired DataModelRepository models;
    private MockMvc mvc;
    private final String prefix = "candidate_" + UUID.randomUUID().toString().replace("-", "");

    @BeforeEach
    void prepareMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void filtersPublishedJdbcConnectionsBeforePaginationAndCombinesPurposeAndKeyword() throws Exception {
        var origin = source("b_origin", DataSourcePurpose.SOURCE, DataSourceType.POSTGRESQL, true);
        var storage = source("c_storage", DataSourcePurpose.STORAGE, DataSourceType.POSTGRESQL, true);
        var empty = source("a_empty", DataSourcePurpose.STORAGE, DataSourceType.POSTGRESQL, true);
        var disabled = source("d_disabled", DataSourcePurpose.STORAGE, DataSourceType.POSTGRESQL, false);
        var kafka = source("e_kafka", DataSourcePurpose.SOURCE, DataSourceType.KAFKA, true);
        var distribution = source("f_distribution", DataSourcePurpose.DISTRIBUTION, DataSourceType.POSTGRESQL, true);
        model(origin, "external", true, PhysicalTableMode.EXTERNAL);
        model(storage, "managed", true, PhysicalTableMode.MANAGED);
        model(storage, "managed_two", true, PhysicalTableMode.MANAGED);
        model(empty, "draft", false, PhysicalTableMode.MANAGED);
        var retired = model(empty, "retired", true, PhysicalTableMode.MANAGED);
        retired.disable();
        models.saveAndFlush(retired);
        model(disabled, "disabled_source", true, PhysicalTableMode.MANAGED);
        // Defensive filtering also excludes an invalid legacy non-JDBC reference.
        model(kafka, "invalid_legacy", true, PhysicalTableMode.EXTERNAL);
        model(distribution, "distribution_only", true, PhysicalTableMode.EXTERNAL);
        String readSearch = "name:*\"" + prefix + "\"* AND enabled:\"true\" AND (sourceEnabled:\"true\" OR storageEnabled:\"true\")";
        mvc.perform(get("/api/v1/data-sources").param("hasPublishedModels", "true")
                        .param("search", readSearch).param("size", "1").param("sort", "name"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(origin.getId().toString()));
        mvc.perform(get("/api/v1/data-sources").param("hasPublishedModels", "true")
                        .param("search", readSearch).param("page", "1").param("size", "1").param("sort", "name"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(storage.getId().toString()));
        mvc.perform(get("/api/v1/data-sources").param("hasPublishedModels", "true")
                        .param("search", readSearch + " AND storageEnabled:\"true\""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(storage.getId().toString()));
        mvc.perform(get("/api/v1/data-sources").param("hasPublishedModels", "true")
                        .param("search", readSearch + " AND name:*\"a_empty\"*"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/data-sources").param("search", "name:*\"" + prefix + "\"*"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    void publishesTheOptionalFilterInRuntimeOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/data-sources'].get.parameters[?(@.name == 'hasPublishedModels')].description")
                        .value(hasItem(containsString("已发布"))));
    }

    private DataSource source(String suffix, DataSourcePurpose purpose, DataSourceType type, boolean enabled) {
        return sources.saveAndFlush(DataSource.create(prefix + "_" + suffix, prefix + "_" + suffix,
                null, Set.of(purpose), type, enabled, null,
                DataSourceConnection.create("localhost", 5432, "test", "public", "test", null, Map.of())));
    }

    private DataModel model(DataSource source, String suffix, boolean published, PhysicalTableMode mode) {
        var model = DataModel.create(prefix + "_" + suffix, suffix, null, source.getId(), null, "public", suffix, mode, null);
        if (published) model.publish();
        return models.saveAndFlush(model);
    }
}
