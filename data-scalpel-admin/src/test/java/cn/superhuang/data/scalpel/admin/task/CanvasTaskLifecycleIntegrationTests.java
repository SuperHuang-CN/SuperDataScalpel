package cn.superhuang.data.scalpel.admin.task;

import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;
import cn.superhuang.data.scalpel.business.compute.client.ComputeEngineDispatcherClient;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineCredentialCipher;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService.ExecutionRoute;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.service.ApiResourceService;
import cn.superhuang.data.scalpel.business.datasource.service.DataSourceRuntimeService;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFieldRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFileRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableSourceRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTablePort;
import cn.superhuang.data.scalpel.business.task.domain.CanvasTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunExecutionMode;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunTriggerType;
import cn.superhuang.data.scalpel.business.task.domain.TaskMisfirePolicy;
import cn.superhuang.data.scalpel.business.task.domain.TaskOverlapPolicy;
import cn.superhuang.data.scalpel.business.task.domain.TaskSchedule;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.execution.repository.TaskExecutionOutboxRepository;
import cn.superhuang.data.scalpel.business.task.repository.CanvasTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskScheduleRepository;
import cn.superhuang.data.scalpel.business.task.service.CanvasTaskRunPreparationService;
import cn.superhuang.data.scalpel.business.task.service.CanvasFileStorageRuntimeProvider;
import cn.superhuang.data.scalpel.business.task.service.DataTaskService;
import cn.superhuang.data.scalpel.business.task.service.TaskCompilationService;
import cn.superhuang.data.scalpel.business.task.service.TaskRunArtifactStorage;
import cn.superhuang.data.scalpel.business.task.service.TaskRunService;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
@Import(CanvasTaskLifecycleIntegrationTests.ExternalDependencyConfiguration.class)
class CanvasTaskLifecycleIntegrationTests {

    @Autowired private DataTaskService service;
    @Autowired private TaskRunService runService;
    @Autowired private DataTaskRepository taskRepository;
    @Autowired private CanvasTaskDefinitionRepository definitionRepository;
    @Autowired private TaskRunRepository runRepository;
    @Autowired private TaskScheduleRepository scheduleRepository;
    @Autowired private TaskExecutionOutboxRepository outboxRepository;
    @Autowired private StubComputeEngineExecutionService executionService;
    @Autowired private StubCanvasTaskRunPreparationService preparationService;
    @Autowired private StubTaskRunArtifactStorage artifactStorage;

    @BeforeEach
    void setUp() {
        executionService.clear();
        preparationService.clear();
        artifactStorage.clear();
    }

    @Test
    void publishesDisablesAndReenablesCanvasThroughTheSharedLifecycle() {
        UUID engineId = UUID.randomUUID();
        DataTask task = configuredCanvasTask(engineId);
        ExecutionRoute route = route(engineId);
        executionService.route = route;
        preparationService.preparation = preparation();

        assertThat(service.publish(task.getId()).status()).isEqualTo(TaskStatus.PUBLISHED);
        assertThat(service.disable(task.getId()).status()).isEqualTo(TaskStatus.DISABLED);
        assertThat(service.enable(task.getId()).status()).isEqualTo(TaskStatus.PUBLISHED);

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.PUBLISHED);
        assertThat(executionService.requireRunnableCalls).isGreaterThanOrEqualTo(2);
        assertThat(executionService.assertUnchangedCalls).isGreaterThanOrEqualTo(2);
        assertThat(preparationService.assertUnchangedCalls).isGreaterThanOrEqualTo(2);
    }

    @Test
    void disablesPublishedCanvasWhenItsDefinitionIsMissingWithoutExternalPreflight() {
        UUID engineId = UUID.randomUUID();
        DataTask task = publishedCanvasTask(engineId);
        definitionRepository.delete(definitionRepository.findByTaskId(task.getId()).orElseThrow());
        definitionRepository.flush();

        var response = service.disable(task.getId());

        assertThat(response.status()).isEqualTo(TaskStatus.DISABLED);
        assertThat(response.definitionConfigured()).isFalse();
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.DISABLED);
        assertThat(executionService.requireRunnableCalls).isZero();
        assertThat(executionService.assertUnchangedCalls).isZero();
        assertThat(preparationService.prepareCalls).isZero();
    }

    @Test
    void keepsCanvasDraftWhenFinalPreflightFails() {
        UUID engineId = UUID.randomUUID();
        DataTask task = configuredCanvasTask(engineId);
        executionService.route = route(engineId);
        preparationService.failure = new ResponseStatusException(HttpStatus.CONFLICT, "Canvas 预检失败");

        assertThatThrownBy(() -> service.publish(task.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo("Canvas 预检失败"));

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.DRAFT);
    }

    @Test
    void createsCanvasRunManifestAndSubmitOutboxTogether() {
        UUID engineId = UUID.randomUUID();
        DataTask task = publishedCanvasTask(engineId);
        prepareExecution(engineId);

        var response = runService.run(task.getId());

        assertThat(response.taskType()).isEqualTo(TaskType.SPARK_CANVAS);
        assertThat(response.status()).isEqualTo(TaskRunStatus.QUEUED);
        assertThat(response.computeEngineId()).isEqualTo(engineId);
        TaskRun run = runRepository.findById(response.id()).orElseThrow();
        assertThat(run.getManifestObjectKey()).endsWith("/attempts/1/manifest.json");
        assertThat(run.getResultObjectKey()).endsWith("/attempts/1/result.json");
        assertThat(run.getLogObjectKey()).endsWith("/attempts/1/console.log");
        assertThat(artifactStorage.objects).containsKey(run.getManifestObjectKey());
        assertThat(outboxRepository.findAll()).singleElement()
                .satisfies(message -> {
                    assertThat(message.getAggregateId()).isEqualTo(run.getExecutionRunId());
                    assertThat(message.getExecutionId()).isEqualTo(run.getExternalExecutionId());
                    assertThat(message.getMessageType()).isEqualTo("SUBMIT_EXECUTION");
                    assertThat(message.getTopic()).isEqualTo("commands.canvas");
                });
    }

    @Test
    void submitsIdempotentScheduledCanvasRunWithAllowWhileAnotherRunIsActive() {
        UUID engineId = UUID.randomUUID();
        DataTask task = publishedCanvasTask(engineId);
        prepareExecution(engineId);
        runService.run(task.getId());
        TaskSchedule schedule = enabledSchedule(task.getId(), TaskOverlapPolicy.ALLOW);
        Instant scheduledFireAt = Instant.parse("2027-01-01T00:00:00Z");

        runService.runScheduled(schedule.getId(), scheduledFireAt);
        runService.runScheduled(schedule.getId(), scheduledFireAt);

        TaskRun scheduled = runRepository.findByScheduleIdAndScheduledFireAt(
                schedule.getId(), scheduledFireAt).orElseThrow();
        assertThat(scheduled.getTaskType()).isEqualTo(TaskType.SPARK_CANVAS);
        assertThat(scheduled.getTriggerType()).isEqualTo(TaskRunTriggerType.SCHEDULED);
        assertThat(scheduled.getExecutionMode()).isEqualTo(TaskRunExecutionMode.REAL);
        assertThat(scheduled.getStatus()).isEqualTo(TaskRunStatus.QUEUED);
        assertThat(scheduled.getExternalExecutionId()).isNotNull();
        assertThat(scheduled.getExecutionRunId()).isNotNull();
        assertThat(scheduled.getManifestObjectKey()).isNotBlank();
        assertThat(runRepository.findAll()).hasSize(2);
        assertThat(outboxRepository.findAll()).hasSize(2);
        assertThat(artifactStorage.objects).hasSize(2);
    }

    @Test
    void skipsScheduledCanvasWithForbidBeforeExternalPreparation() {
        UUID engineId = UUID.randomUUID();
        DataTask task = publishedCanvasTask(engineId);
        runRepository.saveAndFlush(TaskRun.queueDispatchedCanvas(
                UUID.randomUUID(), task.getId(), 1, "{\"schemaVersion\":2}", UUID.randomUUID(), 1,
                Instant.now().plusSeconds(3600), engineId, "commands.canvas"
        ));
        TaskSchedule schedule = enabledSchedule(task.getId(), TaskOverlapPolicy.FORBID);
        Instant scheduledFireAt = Instant.parse("2027-01-01T01:00:00Z");

        runService.runScheduled(schedule.getId(), scheduledFireAt);

        TaskRun skipped = runRepository.findByScheduleIdAndScheduledFireAt(
                schedule.getId(), scheduledFireAt).orElseThrow();
        assertThat(skipped.getTaskType()).isEqualTo(TaskType.SPARK_CANVAS);
        assertThat(skipped.getTriggerType()).isEqualTo(TaskRunTriggerType.SCHEDULED);
        assertThat(skipped.getExecutionMode()).isEqualTo(TaskRunExecutionMode.REAL);
        assertThat(skipped.getStatus()).isEqualTo(TaskRunStatus.SKIPPED);
        assertThat(preparationService.prepareCalls).isZero();
        assertThat(artifactStorage.storeCalls).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void recordsScheduledCanvasSubmissionFailureWithoutOutbox() {
        UUID engineId = UUID.randomUUID();
        DataTask task = publishedCanvasTask(engineId);
        executionService.route = route(engineId);
        preparationService.failure = new ResponseStatusException(HttpStatus.CONFLICT, "Canvas 预检失败");
        TaskSchedule schedule = enabledSchedule(task.getId(), TaskOverlapPolicy.ALLOW);
        Instant scheduledFireAt = Instant.parse("2027-01-01T02:00:00Z");

        runService.runScheduled(schedule.getId(), scheduledFireAt);

        TaskRun failed = runRepository.findByScheduleIdAndScheduledFireAt(
                schedule.getId(), scheduledFireAt).orElseThrow();
        assertThat(failed.getTaskType()).isEqualTo(TaskType.SPARK_CANVAS);
        assertThat(failed.getTriggerType()).isEqualTo(TaskRunTriggerType.SCHEDULED);
        assertThat(failed.getExecutionMode()).isEqualTo(TaskRunExecutionMode.REAL);
        assertThat(failed.getStatus()).isEqualTo(TaskRunStatus.FAILED);
        assertThat(failed.getErrorCode()).isEqualTo("CANVAS_SCHEDULE_SUBMISSION_FAILED");
        assertThat(failed.getMessage()).isEqualTo("Canvas 预检失败");
        assertThat(outboxRepository.count()).isZero();
        assertThat(artifactStorage.objects).isEmpty();
    }

    @Test
    void rejectsActiveCanvasRunBeforeRemotePreflightOrManifestUpload() {
        UUID engineId = UUID.randomUUID();
        DataTask task = publishedCanvasTask(engineId);
        runRepository.saveAndFlush(TaskRun.queueDispatchedCanvas(
                UUID.randomUUID(), task.getId(), 1, "{\"schemaVersion\":2}", UUID.randomUUID(), 1,
                java.time.Instant.now().plusSeconds(3600), engineId, "commands.canvas"
        ));

        assertThatThrownBy(() -> runService.run(task.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("已有正在执行的实例");
                });

        assertThat(executionService.requireRunnableCalls).isZero();
        assertThat(preparationService.prepareCalls).isZero();
        assertThat(artifactStorage.storeCalls).isZero();
    }

    @Test
    void createsNoRunWhenManifestStorageFails() {
        UUID engineId = UUID.randomUUID();
        DataTask task = publishedCanvasTask(engineId);
        prepareExecution(engineId);
        artifactStorage.storeFailure = new IllegalStateException("storage unavailable");

        assertThatThrownBy(() -> runService.run(task.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(runRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void deletesManifestWhenFinalRunTransactionFails() {
        UUID engineId = UUID.randomUUID();
        DataTask task = publishedCanvasTask(engineId);
        prepareExecution(engineId);
        executionService.assertFailure = new ResponseStatusException(HttpStatus.CONFLICT, "路由已变化");

        assertThatThrownBy(() -> runService.run(task.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo("路由已变化"));

        assertThat(runRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
        assertThat(artifactStorage.deletedKeys).singleElement()
                .asString().endsWith("/attempts/1/manifest.json");
        assertThat(artifactStorage.objects).isEmpty();
    }

    @Test
    void requestsCanvasCancellationOnceAndQueuesCancelCommand() {
        UUID engineId = UUID.randomUUID();
        DataTask task = publishedCanvasTask(engineId);
        prepareExecution(engineId);
        var created = runService.run(task.getId());

        assertThat(runService.cancel(created.id()).status()).isEqualTo(TaskRunStatus.CANCEL_REQUESTED);
        assertThat(runService.cancel(created.id()).status()).isEqualTo(TaskRunStatus.CANCEL_REQUESTED);

        assertThat(outboxRepository.findAll())
                .extracting(message -> message.getMessageType())
                .containsExactlyInAnyOrder("SUBMIT_EXECUTION", "CANCEL_EXECUTION");
    }

    @Test
    void readsCanvasResultAndLogButNeverTreatsMissingObjectsAsAvailable() {
        UUID engineId = UUID.randomUUID();
        DataTask task = publishedCanvasTask(engineId);
        prepareExecution(engineId);
        var created = runService.run(task.getId());
        TaskRun run = runRepository.findById(created.id()).orElseThrow();

        assertThatThrownBy(() -> runService.resultArtifact(run.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        artifactStorage.objects.put(run.getResultObjectKey(), "{\"status\":\"SUCCESS\"}".getBytes());
        artifactStorage.objects.put(run.getLogObjectKey(), "runner completed".getBytes());

        assertThat(new String(runService.resultArtifact(run.getId()).content())).contains("SUCCESS");
        assertThat(new String(runService.logArtifact(run.getId()).content())).isEqualTo("runner completed");

        artifactStorage.readFailure = new IllegalStateException("storage unavailable");
        assertThatThrownBy(() -> runService.resultArtifact(run.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        artifactStorage.readFailure = null;

        TaskRun localRun = runRepository.saveAndFlush(TaskRun.queue(UUID.randomUUID(), 1, "{\"sql\":\"select 1\"}"));
        assertThatThrownBy(() -> runService.resultArtifact(localRun.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private DataTask configuredCanvasTask(UUID engineId) {
        DataTask task = taskRepository.saveAndFlush(DataTask.create(
                "canvas-" + UUID.randomUUID(), null, TaskType.SPARK_CANVAS, null, engineId
        ));
        definitionRepository.saveAndFlush(CanvasTaskDefinition.create(
                task.getId(), 1, 1,
                "{\"schemaVersion\":1,\"schemaMinorVersion\":1,\"nodes\":[],\"edges\":[]}"
        ));
        return task;
    }

    private DataTask publishedCanvasTask(UUID engineId) {
        DataTask task = configuredCanvasTask(engineId);
        task.publish();
        return taskRepository.saveAndFlush(task);
    }

    private void prepareExecution(UUID engineId) {
        executionService.route = route(engineId);
        preparationService.preparation = preparation();
    }

    private TaskSchedule enabledSchedule(UUID taskId, TaskOverlapPolicy overlapPolicy) {
        TaskSchedule schedule = TaskSchedule.create(
                taskId,
                "schedule-" + UUID.randomUUID(),
                "0 0 * * * ?",
                "Asia/Shanghai",
                TaskMisfirePolicy.FIRE_ONCE_NOW,
                overlapPolicy
        );
        schedule.enable();
        return scheduleRepository.saveAndFlush(schedule);
    }

    private static ExecutionRoute route(UUID engineId) {
        return new ExecutionRoute(
                engineId, 1, "commands.canvas", "admin.events", ComputeBackendType.LOCAL_DOCKER,
                "http://127.0.0.1:18092", "encrypted-token", "dispatcher-test"
        );
    }

    private static CanvasTaskRunPreparationService.Preparation preparation() {
        return new CanvasTaskRunPreparationService.Preparation(
                new MetadataSnapshot(List.of(), List.of()),
                List.of(), null, Map.of(), Map.of(), null, List.of()
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExternalDependencyConfiguration {

        @Bean
        @Primary
        StubComputeEngineExecutionService canvasLifecycleExecutionService(
                ComputeEngineRepository repository,
                ComputeEngineCredentialCipher credentialCipher,
                ComputeEngineDispatcherClient dispatcherClient,
                PlatformTransactionManager transactionManager
        ) {
            return new StubComputeEngineExecutionService(
                    repository, credentialCipher, dispatcherClient, transactionManager
            );
        }

        @Bean
        @Primary
        StubCanvasTaskRunPreparationService canvasLifecyclePreparationService(
                DataSourceRepository dataSourceRepository,
                DataSourceRuntimeService runtimeService,
                DataModelRepository modelRepository,
                DataModelFieldRepository modelFieldRepository,
                DialectRegistry dialectRegistry,
                TaskCompilationService compilationService,
                ApiResourceService apiResourceService,
                ModelPhysicalTablePort physicalTablePort,
                FileDatasetRepository fileDatasetRepository,
                FileDatasetTableRepository fileDatasetTableRepository,
                FileDatasetFileRepository fileDatasetFileRepository,
                FileDatasetFieldRepository fileDatasetFieldRepository,
                FileDatasetTableSourceRepository fileDatasetTableSourceRepository,
                ObjectProvider<CanvasFileStorageRuntimeProvider> fileStorageRuntimeProvider,
                ObjectMapper objectMapper
        ) {
            return new StubCanvasTaskRunPreparationService(
                    dataSourceRepository, runtimeService, modelRepository, modelFieldRepository,
                    dialectRegistry, compilationService, apiResourceService, physicalTablePort,
                    fileDatasetRepository, fileDatasetTableRepository, fileDatasetFileRepository,
                    fileDatasetFieldRepository, fileDatasetTableSourceRepository,
                    fileStorageRuntimeProvider, objectMapper
            );
        }

        @Bean
        @Primary
        StubTaskRunArtifactStorage canvasLifecycleArtifactStorage() {
            return new StubTaskRunArtifactStorage();
        }
    }

    static final class StubComputeEngineExecutionService extends ComputeEngineExecutionService {
        private ExecutionRoute route;
        private RuntimeException assertFailure;
        private int requireRunnableCalls;
        private int assertUnchangedCalls;

        StubComputeEngineExecutionService(
                ComputeEngineRepository repository,
                ComputeEngineCredentialCipher credentialCipher,
                ComputeEngineDispatcherClient dispatcherClient,
                PlatformTransactionManager transactionManager
        ) {
            super(repository, credentialCipher, dispatcherClient, transactionManager);
        }

        @Override
        public ExecutionRoute requireRunnable(UUID engineId) {
            requireRunnableCalls++;
            if (route == null || !route.engineId().equals(engineId)) {
                throw new AssertionError("未配置预期计算引擎路由");
            }
            return route;
        }

        @Override
        public void assertUnchanged(ExecutionRoute expected) {
            assertUnchangedCalls++;
            if (assertFailure != null) throw assertFailure;
            if (!expected.equals(route)) {
                throw new AssertionError("最终事务使用了非预期计算引擎路由");
            }
        }

        void clear() {
            route = null;
            assertFailure = null;
            requireRunnableCalls = 0;
            assertUnchangedCalls = 0;
        }
    }

    static final class StubCanvasTaskRunPreparationService extends CanvasTaskRunPreparationService {
        private Preparation preparation;
        private RuntimeException failure;
        private int prepareCalls;
        private int assertUnchangedCalls;

        StubCanvasTaskRunPreparationService(
                DataSourceRepository dataSourceRepository,
                DataSourceRuntimeService runtimeService,
                DataModelRepository modelRepository,
                DataModelFieldRepository modelFieldRepository,
                DialectRegistry dialectRegistry,
                TaskCompilationService compilationService,
                ApiResourceService apiResourceService,
                ModelPhysicalTablePort physicalTablePort,
                FileDatasetRepository fileDatasetRepository,
                FileDatasetTableRepository fileDatasetTableRepository,
                FileDatasetFileRepository fileDatasetFileRepository,
                FileDatasetFieldRepository fileDatasetFieldRepository,
                FileDatasetTableSourceRepository fileDatasetTableSourceRepository,
                ObjectProvider<CanvasFileStorageRuntimeProvider> fileStorageRuntimeProvider,
                ObjectMapper objectMapper
        ) {
            super(
                    dataSourceRepository, runtimeService, modelRepository, modelFieldRepository,
                    dialectRegistry, compilationService, apiResourceService, physicalTablePort,
                    fileDatasetRepository, fileDatasetTableRepository, fileDatasetFileRepository,
                    fileDatasetFieldRepository, fileDatasetTableSourceRepository,
                    fileStorageRuntimeProvider, objectMapper
            );
        }

        @Override
        public Preparation prepare(cn.superhuang.data.scalpel.business.task.canvas.CanvasDefinition definition) {
            prepareCalls++;
            if (failure != null) throw failure;
            if (preparation == null) throw new AssertionError("未配置 Canvas 预检结果");
            return preparation;
        }

        @Override
        public Preparation prepare(
                cn.superhuang.data.scalpel.business.task.canvas.CanvasDefinition definition,
                CanvasExecutionMode executionMode
        ) {
            return prepare(definition);
        }

        @Override
        public void assertDataSourcesUnchanged(Map<UUID, java.time.Instant> expectedVersions) {
            assertUnchangedCalls++;
            assertThat(expectedVersions).isEqualTo(Map.of());
        }

        @Override
        public void assertModelsUnchanged(Map<UUID, ModelVersion> expectedVersions) {
            assertUnchangedCalls++;
            assertThat(expectedVersions).isEqualTo(Map.of());
        }

        void clear() {
            preparation = null;
            failure = null;
            prepareCalls = 0;
            assertUnchangedCalls = 0;
        }
    }

    static final class StubTaskRunArtifactStorage implements TaskRunArtifactStorage {
        private final Map<String, byte[]> objects = new HashMap<>();
        private final List<String> deletedKeys = new ArrayList<>();
        private RuntimeException storeFailure;
        private RuntimeException readFailure;
        private int storeCalls;

        @Override
        public void store(String objectKey, byte[] content, String contentType) {
            storeCalls++;
            if (storeFailure != null) throw storeFailure;
            objects.put(objectKey, content.clone());
        }

        @Override
        public void delete(String objectKey) {
            deletedKeys.add(objectKey);
            objects.remove(objectKey);
        }

        @Override
        public URI presignGet(String objectKey, Duration lifetime) {
            return URI.create("https://objects.invalid/" + objectKey);
        }

        @Override
        public URI presignPut(String objectKey, String contentType, Duration lifetime) {
            return URI.create("https://objects.invalid/" + objectKey);
        }

        @Override
        public Optional<byte[]> readIfPresent(String objectKey, int maximumBytes) {
            if (readFailure != null) throw readFailure;
            byte[] content = objects.get(objectKey);
            return content == null ? Optional.empty() : Optional.of(content.clone());
        }

        void clear() {
            objects.clear();
            deletedKeys.clear();
            storeFailure = null;
            readFailure = null;
            storeCalls = 0;
        }
    }
}
