package cn.superhuang.data.scalpel.admin.compute;

import cn.superhuang.data.scalpel.business.compute.client.ComputeEngineDispatcherClient;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherCapabilities;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherInfoResponse;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherRegistrationRequest;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherRegistrationResponse;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherRegistrationState;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngine;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineHealthState;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineRegistrationState;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineCredentialCipher;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineManagementService;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineProperties;
import cn.superhuang.data.scalpel.business.compute.web.request.DetachComputeEngineRequest;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.execution.domain.TaskExecutionOutboxMessage;
import cn.superhuang.data.scalpel.business.task.execution.repository.TaskExecutionOutboxRepository;
import cn.superhuang.data.scalpel.business.task.execution.service.ExecutionKafkaProperties;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.contract.execution.ExecutionArtifactLocation;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.execution.SubmitExecutionCommand;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class ComputeEngineDetachmentIntegrationTests {

    @Autowired ComputeEngineRepository repository;
    @Autowired DataTaskRepository taskRepository;
    @Autowired TaskRunRepository taskRunRepository;
    @Autowired TaskExecutionOutboxRepository executionOutboxRepository;
    @Autowired SearchEngine searchEngine;
    @Autowired ComputeEngineCredentialCipher cipher;
    @Autowired PlatformTransactionManager transactionManager;

    private final List<UUID> engineIds = new ArrayList<>();
    private final List<UUID> taskRunIds = new ArrayList<>();
    private final List<UUID> outboxIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        executionOutboxRepository.deleteAllById(outboxIds);
        taskRunRepository.deleteAllById(taskRunIds);
        repository.deleteAllById(engineIds);
    }

    @Test
    void detachesOnlyAfterDispatcherTransportFailureAndClearsRemoteIdentity() {
        ComputeEngine engine = activeEngine();
        StubDispatcherClient client = new StubDispatcherClient();

        var response = service(client).detach(
                engine.getId(),
                new DetachComputeEngineRequest(engine.getName(), "原 Dispatcher 主机已永久下线")
        );

        assertThat(response.registrationState()).isEqualTo(ComputeEngineRegistrationState.DETACHED);
        assertThat(response.healthState()).isEqualTo(ComputeEngineHealthState.DOWN);
        assertThat(response.dispatcherInstanceId()).isNull();
        assertThat(response.reportedBackendType()).isNull();
        assertThat(response.detachedAt()).isNotNull();
        assertThat(response.detachReason()).isEqualTo("原 Dispatcher 主机已永久下线");

        ComputeEngine current = repository.findById(engine.getId()).orElseThrow();
        assertThat(current.getRegistrationState()).isEqualTo(ComputeEngineRegistrationState.DETACHED);
        assertThat(current.getLastError()).contains("已离线解除绑定");
        assertThat(client.infoCalls).isEqualTo(1);
    }

    @Test
    void refusesOfflineDetachWhenDispatcherIsReachable() {
        ComputeEngine engine = activeEngine();
        StubDispatcherClient client = new StubDispatcherClient();
        client.infoMode = InfoMode.REACHABLE;

        assertThatThrownBy(() -> service(client).detach(
                engine.getId(),
                new DetachComputeEngineRequest(engine.getName(), "误判测试")
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getReason()).contains("仍可访问");
        });

        assertThat(repository.findById(engine.getId()).orElseThrow().getRegistrationState())
                .isEqualTo(ComputeEngineRegistrationState.ACTIVE);
    }

    @Test
    void refusesOfflineDetachForHttpOrAuthenticationFailures() {
        ComputeEngine engine = activeEngine();
        StubDispatcherClient client = new StubDispatcherClient();
        client.infoMode = InfoMode.APPLICATION_ERROR;

        assertThatThrownBy(() -> service(client).detach(
                engine.getId(),
                new DetachComputeEngineRequest(engine.getName(), "认证配置错误")
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getReason()).contains("不是连接不可达");
        });

        assertThat(repository.findById(engine.getId()).orElseThrow().getRegistrationState())
                .isEqualTo(ComputeEngineRegistrationState.ACTIVE);
    }

    @Test
    void rejectsWrongEngineNameBeforeProbingDispatcher() {
        ComputeEngine engine = activeEngine();
        StubDispatcherClient client = new StubDispatcherClient();

        assertThatThrownBy(() -> service(client).detach(
                engine.getId(),
                new DetachComputeEngineRequest("错误名称", "原 Dispatcher 主机已永久下线")
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(exception.getReason()).contains("确认名称");
        });

        assertThat(client.infoCalls).isZero();
    }

    @Test
    void blocksOfflineDetachWhileTaskRunIsActive() {
        ComputeEngine engine = activeEngine();
        TaskRun run = taskRunRepository.saveAndFlush(TaskRun.queueDispatchedCanvas(
                UUID.randomUUID(), UUID.randomUUID(), 1, "{}",
                UUID.randomUUID(), 1, Instant.now().plusSeconds(3600),
                engine.getId(), engine.getCommandTopic()
        ));
        taskRunIds.add(run.getId());

        assertThatThrownBy(() -> service(new StubDispatcherClient()).detach(
                engine.getId(),
                new DetachComputeEngineRequest(engine.getName(), "原 Dispatcher 主机已永久下线")
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getReason()).contains("排队、运行或待取消任务");
        });

        assertThat(repository.findById(engine.getId()).orElseThrow().getRegistrationState())
                .isEqualTo(ComputeEngineRegistrationState.ACTIVE);
    }

    @Test
    void blocksOfflineDetachWhileKafkaOutboxHasUnpublishedCommand() {
        ComputeEngine engine = activeEngine();
        UUID runId = UUID.randomUUID();
        Instant now = Instant.now();
        SubmitExecutionCommand command = new SubmitExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION, now,
                engine.getId(), UUID.randomUUID(), runId, 1, UUID.randomUUID(),
                ExecutionTaskType.SPARK_CANVAS, 1, now.plusSeconds(3600),
                new ExecutionArtifactLocation(
                        "task-runs/" + runId + "/attempts/1/manifest.json",
                        "a".repeat(64),
                        "task-runs/" + runId + "/attempts/1/result.json",
                        "task-runs/" + runId + "/attempts/1/console.log"
                )
        );
        TaskExecutionOutboxMessage outbox = executionOutboxRepository.saveAndFlush(
                TaskExecutionOutboxMessage.pending(engine.getCommandTopic(), command, "{}")
        );
        outboxIds.add(outbox.getId());

        assertThatThrownBy(() -> service(new StubDispatcherClient()).detach(
                engine.getId(),
                new DetachComputeEngineRequest(engine.getName(), "原 Dispatcher 主机已永久下线")
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getReason()).contains("尚未发布完成");
        });
    }

    @Test
    void canRegisterDetachedEngineAfterOperatorConfirmsOldDispatcherHasStopped() {
        ComputeEngine engine = activeEngine();
        StubDispatcherClient client = new StubDispatcherClient();
        ComputeEngineManagementService service = service(client);
        service.detach(
                engine.getId(),
                new DetachComputeEngineRequest(engine.getName(), "原 Dispatcher 主机已永久下线")
        );

        client.infoMode = InfoMode.REACHABLE;
        var response = service.register(engine.getId());

        assertThat(response.registrationState()).isEqualTo(ComputeEngineRegistrationState.ACTIVE);
        assertThat(response.healthState()).isEqualTo(ComputeEngineHealthState.UP);
        assertThat(response.dispatcherInstanceId()).isEqualTo("dispatcher-replacement");
        assertThat(response.detachedAt()).isNull();
        assertThat(response.detachReason()).isNull();
    }

    private ComputeEngineManagementService service(ComputeEngineDispatcherClient client) {
        return new ComputeEngineManagementService(
                repository, taskRepository, taskRunRepository, executionOutboxRepository,
                searchEngine, cipher, client,
                new ExecutionKafkaProperties(
                        "test-admin", List.of("admin.events"), "invalid.events", 50,
                        Duration.ofMillis(500), Duration.ofSeconds(10), Duration.ofMinutes(1)
                ),
                transactionManager
        );
    }

    private ComputeEngine activeEngine() {
        String suffix = UUID.randomUUID().toString();
        ComputeEngine engine = ComputeEngine.create(
                "engine-" + suffix, "test", "http://127.0.0.1:18092",
                cipher.encrypt("secret"), ComputeBackendType.LOCAL_DOCKER,
                "commands." + suffix, "runner." + suffix, "admin.events", 20, 2, 2
        );
        engine.activate("dispatcher-old", ComputeBackendType.LOCAL_DOCKER);
        ComputeEngine saved = repository.saveAndFlush(engine);
        engineIds.add(saved.getId());
        return saved;
    }

    private enum InfoMode {
        UNREACHABLE,
        REACHABLE,
        APPLICATION_ERROR
    }

    private static final class StubDispatcherClient extends ComputeEngineDispatcherClient {
        private InfoMode infoMode = InfoMode.UNREACHABLE;
        private int infoCalls;

        private StubDispatcherClient() {
            super(new ComputeEngineProperties(null, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        }

        @Override
        public DispatcherInfoResponse info(String baseUrl, String token) {
            infoCalls++;
            if (infoMode == InfoMode.UNREACHABLE) {
                throw new ResourceAccessException("Connection refused");
            }
            if (infoMode == InfoMode.APPLICATION_ERROR) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "访问令牌无效");
            }
            return new DispatcherInfoResponse(
                    "dispatcher-replacement", ComputeBackendType.LOCAL_DOCKER, "test",
                    new DispatcherCapabilities(true, true, true), List.of()
            );
        }

        @Override
        public DispatcherRegistrationResponse activate(
                String baseUrl,
                String token,
                DispatcherRegistrationRequest request
        ) {
            return new DispatcherRegistrationResponse(
                    request.engineId(), "dispatcher-replacement", ComputeBackendType.LOCAL_DOCKER,
                    DispatcherRegistrationState.ACTIVE,
                    request.topics(), request.admissionPolicy(), null
            );
        }
    }
}
