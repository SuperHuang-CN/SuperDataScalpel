package cn.superhuang.data.scalpel.admin.operations;

import cn.superhuang.data.scalpel.business.model.domain.*;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.service.DataModelService;
import cn.superhuang.data.scalpel.business.quality.service.ModelQualityStatisticsService;
import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.business.task.repository.*;
import cn.superhuang.data.scalpel.business.task.service.DataTaskService;
import cn.superhuang.data.scalpel.business.service.DataServiceManagementService;
import cn.superhuang.data.scalpel.business.service.domain.*;
import cn.superhuang.data.scalpel.business.service.repository.*;
import cn.superhuang.data.scalpel.business.operations.service.RuntimeWorkbenchService;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import cn.superhuang.data.scalpel.contract.execution.StreamingCheckpointMode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(properties = "data-scalpel.operations.background-enabled=false")
@Transactional
class HomepageStatisticsIntegrationTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    cn.superhuang.data.scalpel.business.task.service.TaskRunArtifactStorage artifactStorage;
    // RBAC bootstrap takes a PostgreSQL table lock; the H2 test only exercises statistics and HTTP authorization.
    @org.springframework.test.context.bean.override.mockito.MockitoBean(name = "initializeSystemAccess")
    org.springframework.boot.ApplicationRunner accessInitializer;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    cn.superhuang.data.scalpel.business.operations.service.OperationsAccess operationsAccess;
    @Autowired DataModelRepository models;
    @Autowired DataModelService modelService;
    @Autowired ModelQualityStatisticsService quality;
    @Autowired TaskRunRepository runs;
    @Autowired DataTaskRepository tasks;
    @Autowired DataTaskService taskService;
    @Autowired DataServiceRepository services;
    @Autowired DataServiceDeploymentRepository deployments;
    @Autowired DataServiceManagementService serviceService;
    @Autowired RuntimeWorkbenchService runtime;
    @Autowired org.springframework.web.context.WebApplicationContext context;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired cn.superhuang.data.scalpel.business.service.accesslog.service.GatewayAccessQueryService gateway;
    @Autowired cn.superhuang.data.scalpel.business.asset.service.AssetManagementService assets;
    private final Instant base = Instant.parse("2026-09-29T00:00:00Z");

    @AfterEach void clearAuthentication() {
        SecurityContextHolder.clearContext();
        org.mockito.Mockito.verifyNoInteractions(artifactStorage);
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(authorities = "model.view")
    void resourcesEnforceDomainPermissionsAndUseProblemDetails() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/models/statistics"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.published").value(0));
        for (String path : List.of("tasks/statistics", "data-services/statistics", "assets/statistics", "gateway-access-statistics/usage")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/" + path))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentTypeCompatibleWith("application/problem+json"));
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/model-quality/models?result=bad"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").exists());
    }

    @Test void modelCountsKeepDraftAndDanglingLayerSeparateAndPreserveEmptyLayers() {
        model("published-managed", DataModelStatus.PUBLISHED, PhysicalTableMode.MANAGED, null);
        model("published-external", DataModelStatus.PUBLISHED, PhysicalTableMode.EXTERNAL, UUID.randomUUID());
        model("draft", DataModelStatus.DRAFT, PhysicalTableMode.MANAGED, null);
        model("disabled", DataModelStatus.DISABLED, PhysicalTableMode.EXTERNAL, null);
        var result = modelService.statistics();
        assertThat(result.published()).isEqualTo(2);
        assertThat(result.draft()).isEqualTo(1);
        assertThat(result.managed()).isEqualTo(1);
        assertThat(result.external()).isEqualTo(1);
        assertThat(result.layers()).filteredOn(layer -> layer.id() == null).singleElement().satisfies(layer -> assertThat(layer.count()).isEqualTo(1));
        assertThat(result.layers()).filteredOn(layer -> layer.name().equals("分层不可用")).singleElement();
        assertThat(result.layers().stream().mapToLong(layer -> layer.count()).sum()).isEqualTo(2);
    }

    @Test void qualityUsesLatestValidRealResultPerPublishedModelAndKeepsExecutionFailureSeparate() {
        var a = model("a", DataModelStatus.PUBLISHED, PhysicalTableMode.MANAGED, null);
        var b = model("b", DataModelStatus.PUBLISHED, PhysicalTableMode.MANAGED, null);
        var c = model("c", DataModelStatus.PUBLISHED, PhysicalTableMode.MANAGED, null);
        var draft = model("draft", DataModelStatus.DRAFT, PhysicalTableMode.MANAGED, null);
        qualityRun(a.getId(), 1, TaskRunExecutionMode.REAL, TaskRunStatus.SUCCESS, QualityConclusion.FAILED, true);
        var valid = qualityRun(a.getId(), 2, TaskRunExecutionMode.REAL, TaskRunStatus.SUCCESS, QualityConclusion.PASSED, true);
        qualityRun(a.getId(), 3, TaskRunExecutionMode.TRIAL, TaskRunStatus.SUCCESS, QualityConclusion.FAILED, true);
        qualityRun(a.getId(), 4, TaskRunExecutionMode.REAL, TaskRunStatus.FAILED, null, true);
        qualityRun(b.getId(), 1, TaskRunExecutionMode.SIMULATED, TaskRunStatus.SUCCESS, QualityConclusion.PASSED, true);
        qualityRun(b.getId(), 2, TaskRunExecutionMode.REAL, TaskRunStatus.SUCCESS, QualityConclusion.PASSED, false);
        qualityRun(c.getId(), 1, TaskRunExecutionMode.REAL, TaskRunStatus.SUCCESS, QualityConclusion.FAILED, true);
        qualityRun(draft.getId(), 1, TaskRunExecutionMode.REAL, TaskRunStatus.SUCCESS, QualityConclusion.PASSED, true);
        var summary = quality.statistics();
        assertThat(summary.total()).isEqualTo(3);
        assertThat(summary.passed()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.noResult()).isEqualTo(1);
        assertThat(summary.latestExecutionFailed()).isEqualTo(1);
        assertThat(quality.items("PASSED",0,20).content()).singleElement().satisfies(row -> {
            assertThat(row.runId()).isEqualTo(valid.getId());
            assertThat(row.latestExecutionFailed()).isTrue();
        });
        assertThat(quality.items("NONE",0,20).content()).singleElement().satisfies(row -> assertThat(row.modelId()).isEqualTo(b.getId()));
        assertThat(quality.items("EXECUTION_FAILED",0,20).totalElements()).isEqualTo(1);
        assertThat(quality.items("ALL",1,2).content()).hasSize(1);
        assertThat(quality.items("ALL",2,2).content()).isEmpty();
        assertThatThrownBy(() -> quality.items("unknown",0,20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> quality.items("ALL",0,101)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void equalResultTimesStillChooseExactlyOneResult() {
        var model = model("tie", DataModelStatus.PUBLISHED, PhysicalTableMode.MANAGED, null);
        qualityRun(model.getId(), 1, TaskRunExecutionMode.REAL, TaskRunStatus.SUCCESS, QualityConclusion.PASSED, true);
        qualityRun(model.getId(), 1, TaskRunExecutionMode.REAL, TaskRunStatus.SUCCESS, QualityConclusion.FAILED, true);
        assertThat(quality.statistics().total()).isEqualTo(1);
        assertThat(quality.items("ALL",0,20).content()).hasSize(1);
    }

    @Test void definitionsAreCountedByAllSevenTypesIndependentlyOfRuns() {
        for (var type : TaskType.values()) tasks.save(DataTask.create(type.name(), null, type, null));
        tasks.flush();
        var stats = taskService.statistics();
        assertThat(stats.total()).isEqualTo(TaskType.values().length);
        assertThat(stats.types()).hasSize(TaskType.values().length).allSatisfy(row -> assertThat(row.count()).isEqualTo(1));
    }

    @Test void serviceCountsSeparateFailedMissingAndPendingDeploymentsFromEnabledDefinitions() {
        service("deployed", true, DataServiceDeploymentStatus.DEPLOYED);
        service("failed", true, DataServiceDeploymentStatus.FAILED);
        service("pending", true, DataServiceDeploymentStatus.PENDING);
        service("missing", true, null);
        service("disabled", false, DataServiceDeploymentStatus.FAILED);
        var stats = serviceService.statistics();
        assertThat(stats.enabled()).isEqualTo(4);
        assertThat(stats.failed()).isEqualTo(1);
        assertThat(stats.unconfirmed()).isEqualTo(2);
        assertThat(stats.gatewayPublished()).isZero();
        assertThat(stats.types()).hasSize(4);
        var existing = services.findAll().getFirst().getId();
        for (var provider : List.of(cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider.KONG,
                cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider.APISIX)) {
            var binding = cn.superhuang.data.scalpel.business.service.gateway.domain.GatewayServiceBinding.publishing(existing, provider);
            binding.beginPublish("/open-api/v1/published", DataServiceAccessMode.PUBLIC);
            binding.publishedWith("service", "route", "http://gateway/open-api/v1/published", 1);
            em.persist(binding);
        }
        var deleted = cn.superhuang.data.scalpel.business.service.gateway.domain.GatewayServiceBinding.publishing(
            UUID.randomUUID(), cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider.KONG);
        deleted.beginPublish("/open-api/v1/deleted", DataServiceAccessMode.PUBLIC);
        deleted.publishedWith("deleted", "deleted", "http://gateway/open-api/v1/deleted", 1);
        em.persist(deleted); em.flush();
        assertThat(serviceService.statistics().gatewayPublished()).isEqualTo(1);
    }

    @Test void runtimeTypesExcludeTrialsAndKeepCurrentRunsOutsideHistoricalWindow() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","unused",List.of()));
        org.mockito.Mockito.when(operationsAccess.actor()).thenReturn(new cn.superhuang.data.scalpel.business.operations.service.OperationsAccess.Actor(
            UUID.randomUUID(), "admin", Set.of("task.view")));
        var real = qualityRun(UUID.randomUUID(), 1, TaskRunExecutionMode.REAL, TaskRunStatus.SUCCESS, QualityConclusion.FAILED, true);
        qualityRun(UUID.randomUUID(), 1, TaskRunExecutionMode.TRIAL, TaskRunStatus.SUCCESS, QualityConclusion.PASSED, true);
        var queued = TaskRun.queue(UUID.randomUUID(),1,"{}");
        queued.useTaskType(TaskType.SPARK_JAR);
        ReflectionTestUtils.setField(queued,"queuedAt",base.minusSeconds(90000));
        runs.saveAndFlush(queued);
        var overview = runtime.overview(base,base.plusSeconds(3600));
        assertThat(overview.tasks().completed().get(TaskRunStatus.SUCCESS)).isEqualTo(1);
        assertThat(overview.tasks().current().get(TaskRunStatus.QUEUED)).isEqualTo(1);
        assertThat(overview.tasks().types()).filteredOn(row -> row.type() == real.getTaskType()).singleElement()
            .satisfies(row -> assertThat(row.completed().get(TaskRunStatus.SUCCESS)).isEqualTo(1));
        assertThat(overview.tasks().types()).filteredOn(row -> row.type() == TaskType.SPARK_JAR).singleElement()
            .satisfies(row -> assertThat(row.current().get(TaskRunStatus.QUEUED)).isEqualTo(1));
    }

    @Test void streamingSummaryIncludesActiveAndLatestRealGenerationsWithoutOldStoppedOrTrialCounts() {
        org.mockito.Mockito.when(operationsAccess.actor()).thenReturn(new cn.superhuang.data.scalpel.business.operations.service.OperationsAccess.Actor(
            UUID.randomUUID(), "admin", Set.of("task.view")));
        var task = tasks.saveAndFlush(DataTask.create("stream",null,TaskType.SPARK_STREAMING_CANVAS,null));
        var engine = UUID.randomUUID();
        for (int generation = 1; generation <= 3; generation++) {
            var deployment = TaskStreamingDeployment.create(task.getId(),1,engine,"checkpoint-"+generation,generation,StreamingCheckpointMode.FRESH,null);
            if (generation == 2) { deployment.beginStart(UUID.randomUUID()); deployment.markRunning(base); }
            em.persist(deployment);
        }
        var trial = TaskStreamingDeployment.createTrial(task.getId(),1,engine,"trial",4);
        trial.beginStart(UUID.randomUUID()); trial.markRunning(base); em.persist(trial); em.flush();
        var overview = runtime.overview(base,base.plusSeconds(3600));
        assertThat(overview.tasks().types()).filteredOn(row -> row.type() == TaskType.SPARK_STREAMING_CANVAS).singleElement().satisfies(row -> {
            assertThat(row.deployments().get(StreamingDeploymentActualState.RUNNING)).isEqualTo(1);
            assertThat(row.deployments().get(StreamingDeploymentActualState.STOPPED)).isEqualTo(1);
            assertThat(row.completed()).isEmpty();
        });
    }

    @Test void gatewayUsageDeduplicatesAcrossHoursAndServicesAndExcludesWindowEnd() {
        var service = UUID.randomUUID(); var consumer = UUID.randomUUID();
        hourly(service, null, 0, 3);
        hourly(service, null, 3600, 2);
        hourly(UUID.randomUUID(), null, 0, 0);
        hourly(UUID.randomUUID(), null, 7200, 100);
        hourly(service, consumer, 0, 3);
        hourly(service, consumer, 3600, 2);
        hourly(UUID.randomUUID(), consumer, 0, 4);
        em.flush();
        var result = gateway.usage(base, base.plusSeconds(7200));
        assertThat(result.hasSamples()).isTrue();
        assertThat(result.activeServices()).isEqualTo(1);
        assertThat(result.activeConsumers()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(5);
        assertThat(result.serverErrorCount()).isEqualTo(3);
        assertThat(result.latestRecordedHour()).isEqualTo(base.plusSeconds(3600));
        var empty = gateway.usage(base.minusSeconds(7200),base);
        assertThat(empty.hasSamples()).isFalse();
        assertThat(empty.latestRecordedHour()).isNull();
    }

    @Test void assetPublicationAndSourceProblemsAreIndependentOfModelPublication() {
        for (var sync : cn.superhuang.data.scalpel.business.asset.domain.AssetSyncStatus.values()) {
            var asset = cn.superhuang.data.scalpel.business.asset.domain.Asset.register(
                cn.superhuang.data.scalpel.business.asset.domain.AssetType.DATA_MODEL, UUID.randomUUID());
            ReflectionTestUtils.setField(asset,"status",cn.superhuang.data.scalpel.business.asset.domain.AssetStatus.PUBLISHED);
            ReflectionTestUtils.setField(asset,"syncStatus",sync);
            ReflectionTestUtils.setField(asset,"sourceName",sync.name());
            ReflectionTestUtils.setField(asset,"sourceStatus","PUBLISHED");
            ReflectionTestUtils.setField(asset,"sourceSnapshot","{}");
            ReflectionTestUtils.setField(asset,"sourceFingerprint","test");
            ReflectionTestUtils.setField(asset,"lastCheckedAt",base);
            ReflectionTestUtils.setField(asset,"lastSyncedAt",base);
            em.persist(asset);
        }
        em.flush();
        var result = assets.statistics();
        assertThat(result.published()).isEqualTo(5);
        assertThat(result.outdated()).isEqualTo(1);
        assertThat(result.sourceIssues()).isEqualTo(3);
        assertThat(modelService.statistics().published()).isZero();
    }

    private void hourly(UUID serviceId, UUID consumerId, int seconds, long successes) {
        Class<? extends cn.superhuang.data.scalpel.business.service.accesslog.domain.GatewayAccessHourlyStat> type = consumerId == null
            ? cn.superhuang.data.scalpel.business.service.accesslog.domain.GatewayAccessServiceHourlyStat.class
            : cn.superhuang.data.scalpel.business.service.accesslog.domain.GatewayAccessConsumerServiceHourlyStat.class;
        var value = org.springframework.beans.BeanUtils.instantiateClass(type);
        ReflectionTestUtils.setField(value,"dataServiceId",serviceId);
        if (consumerId != null) ReflectionTestUtils.setField(value,"consumerId",consumerId);
        ReflectionTestUtils.setField(value,"hourStart",base.plusSeconds(seconds));
        ReflectionTestUtils.setField(value,"gatewayProvider",cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider.KONG);
        ReflectionTestUtils.setField(value,"status2xxCount",successes);
        ReflectionTestUtils.setField(value,"status5xxCount",1L);
        ReflectionTestUtils.setField(value,"requestCount",successes+1);
        em.persist(value);
    }

    private DataModel model(String code, DataModelStatus status, PhysicalTableMode mode, UUID layer) {
        var value=DataModel.create(code,code,null,layer,UUID.randomUUID(),null,"public",code,mode,null);
        ReflectionTestUtils.setField(value,"status",status);
        return models.saveAndFlush(value);
    }
    private TaskRun qualityRun(UUID modelId, int seconds, TaskRunExecutionMode mode, TaskRunStatus status, QualityConclusion conclusion, boolean snapshot) {
        var value=TaskRun.queue(UUID.randomUUID(),1,"{}");
        value.useTaskType(TaskType.SPARK_MODEL_QUALITY);
        ReflectionTestUtils.setField(value,"qualityTargetModelId",modelId);
        ReflectionTestUtils.setField(value,"qualityRuleSnapshotAt",snapshot ? base : null);
        ReflectionTestUtils.setField(value,"executionMode",mode);
        ReflectionTestUtils.setField(value,"status",status);
        ReflectionTestUtils.setField(value,"qualityConclusion",conclusion);
        ReflectionTestUtils.setField(value,"queuedAt",base.plusSeconds(seconds));
        ReflectionTestUtils.setField(value,"endedAt",base.plusSeconds(seconds));
        return runs.saveAndFlush(value);
    }
    private void service(String code, boolean enabled, DataServiceDeploymentStatus status) {
        var engine=UUID.randomUUID();
        var value=DataService.create(code,code,null,DataServiceType.SQL_QUERY,engine,"/open-api/v1/"+code,null);
        if (enabled) value.markEnabled();
        services.saveAndFlush(value);
        if (status != null) {
            var deployment=DataServiceDeployment.pending(value.getId(),1,engine,"digest","{}");
            ReflectionTestUtils.setField(deployment,"status",status);
            deployments.saveAndFlush(deployment);
        }
    }
}

