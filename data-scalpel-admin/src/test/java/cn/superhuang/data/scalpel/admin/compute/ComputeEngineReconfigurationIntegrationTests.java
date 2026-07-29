package cn.superhuang.data.scalpel.admin.compute;

import cn.superhuang.data.scalpel.business.compute.client.ComputeEngineDispatcherClient;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherAdmissionPolicy;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherCapabilities;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherInfoResponse;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherRegistrationRequest;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherRegistrationResponse;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherRegistrationState;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherTopics;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngine;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineHealthState;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineRegistrationState;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineCredentialCipher;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineManagementService;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineProperties;
import cn.superhuang.data.scalpel.business.compute.web.request.UpdateComputeEngineRequest;
import cn.superhuang.data.scalpel.business.task.execution.service.ExecutionKafkaProperties;
import cn.superhuang.data.scalpel.business.task.execution.repository.TaskExecutionOutboxRepository;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ComputeEngineReconfigurationIntegrationTests {

    @Autowired ComputeEngineRepository repository;
    @Autowired DataTaskRepository taskRepository;
    @Autowired TaskRunRepository taskRunRepository;
    @Autowired TaskExecutionOutboxRepository executionOutboxRepository;
    @Autowired SearchEngine searchEngine;
    @Autowired ComputeEngineCredentialCipher cipher;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void drainsDeactivatesAppliesAndRegistersActiveEngine() {
        ComputeEngine engine = activeEngine();
        StubDispatcherClient client = new StubDispatcherClient(engine);
        ComputeEngineManagementService service = service(client);

        var response = service.reconfigure(engine.getId(), changedRequest());

        assertThat(response.registrationState()).isEqualTo(ComputeEngineRegistrationState.ACTIVE);
        assertThat(response.dispatcherBaseUrl()).isEqualTo("http://127.0.0.1:28092");
        assertThat(response.commandTopic()).endsWith(".next");
        assertThat(response.dispatcherInstanceId()).isEqualTo("dispatcher-next");
        assertThat(client.calls).containsExactly(
                "info", "registration", "drain", "registration", "deactivate", "info", "activate"
        );
    }

    @Test
    void keepsOldConfigurationAndDrainingStateWhileExecutionsRemain() {
        ComputeEngine engine = activeEngine();
        String originalUrl = engine.getDispatcherBaseUrl();
        StubDispatcherClient client = new StubDispatcherClient(engine);
        client.deactivationConflict = true;

        assertThatThrownBy(() -> service(client).reconfigure(engine.getId(), changedRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("已进入排空状态");
                });

        ComputeEngine current = repository.findById(engine.getId()).orElseThrow();
        assertThat(current.getRegistrationState()).isEqualTo(ComputeEngineRegistrationState.DRAINING);
        assertThat(current.getHealthState()).isEqualTo(ComputeEngineHealthState.UP);
        assertThat(current.getDispatcherBaseUrl()).isEqualTo(originalUrl);
    }

    @Test
    void retriesFromDrainingWithoutSendingAnotherDrain() {
        ComputeEngine engine = activeEngine();
        engine.markDraining();
        repository.saveAndFlush(engine);
        StubDispatcherClient client = new StubDispatcherClient(engine);

        var response = service(client).reconfigure(engine.getId(), changedRequest());

        assertThat(response.registrationState()).isEqualTo(ComputeEngineRegistrationState.ACTIVE);
        assertThat(client.calls).containsExactly("info", "registration", "deactivate", "info", "activate");
    }

    @Test
    void candidatePreflightFailureDoesNotDrainActiveEngine() {
        ComputeEngine engine = activeEngine();
        StubDispatcherClient client = new StubDispatcherClient(engine);
        client.infoFailure = true;

        assertThatThrownBy(() -> service(client).reconfigure(engine.getId(), changedRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));

        ComputeEngine current = repository.findById(engine.getId()).orElseThrow();
        assertThat(current.getRegistrationState()).isEqualTo(ComputeEngineRegistrationState.ACTIVE);
        assertThat(client.calls).containsExactly("info");
    }

    @Test
    void keepsNewConfigurationInErrorWhenRegistrationFails() {
        ComputeEngine engine = activeEngine();
        StubDispatcherClient client = new StubDispatcherClient(engine);
        client.activationFailure = true;

        assertThatThrownBy(() -> service(client).reconfigure(engine.getId(), changedRequest()))
                .isInstanceOf(ResponseStatusException.class);

        ComputeEngine current = repository.findById(engine.getId()).orElseThrow();
        assertThat(current.getRegistrationState()).isEqualTo(ComputeEngineRegistrationState.ERROR);
        assertThat(current.getDispatcherBaseUrl()).isEqualTo("http://127.0.0.1:28092");
        assertThat(current.getLastError()).isNotBlank();
    }

    @Test
    void noOpConfigurationDoesNotCallDispatcher() {
        ComputeEngine engine = activeEngine();
        StubDispatcherClient client = new StubDispatcherClient(engine);
        UpdateComputeEngineRequest request = new UpdateComputeEngineRequest(
                engine.getName(), engine.getDescription(), engine.getDispatcherBaseUrl(), null,
                engine.getExpectedBackendType(), engine.getCommandTopic(), engine.getRunnerEventTopic(),
                engine.getAdminEventTopic(), engine.getMaxQueuedExecutions(),
                engine.getMaxConcurrentSubmissions(), engine.getMaxInFlightApplications()
        );

        var response = service(client).reconfigure(engine.getId(), request);

        assertThat(response.registrationState()).isEqualTo(ComputeEngineRegistrationState.ACTIVE);
        assertThat(client.calls).isEmpty();
    }

    @Test
    void refusesToDeactivateRegistrationOwnedByAnotherEngineBeforeSendingCommand() {
        ComputeEngine engine = activeEngine();
        StubDispatcherClient client = new StubDispatcherClient(engine);
        client.remoteEngineId = UUID.randomUUID();

        assertThatThrownBy(() -> service(client).deactivate(engine.getId(), false))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("当前绑定的不是该计算引擎");
                });

        assertThat(repository.findById(engine.getId()).orElseThrow().getRegistrationState())
                .isEqualTo(ComputeEngineRegistrationState.ACTIVE);
        assertThat(client.calls).containsExactly("registration");
    }

    @Test
    void refusesToDeactivateWhenRemoteConfigurationDiffersBeforeSendingCommand() {
        ComputeEngine engine = activeEngine();
        StubDispatcherClient client = new StubDispatcherClient(engine);
        client.remoteConfigurationMismatch = true;

        assertThatThrownBy(() -> service(client).deactivate(engine.getId(), false))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("实际配置不一致");
                });

        assertThat(repository.findById(engine.getId()).orElseThrow().getRegistrationState())
                .isEqualTo(ComputeEngineRegistrationState.ACTIVE);
        assertThat(client.calls).containsExactly("registration");
    }

    @Test
    void rejectsAdminEventTopicThatThisAdminDoesNotListenToBeforeDraining() {
        ComputeEngine engine = activeEngine();
        StubDispatcherClient client = new StubDispatcherClient(engine);
        UpdateComputeEngineRequest request = new UpdateComputeEngineRequest(
                engine.getName(), engine.getDescription(), engine.getDispatcherBaseUrl(), null,
                engine.getExpectedBackendType(), engine.getCommandTopic(), engine.getRunnerEventTopic(),
                "unhandled.events", engine.getMaxQueuedExecutions(),
                engine.getMaxConcurrentSubmissions(), engine.getMaxInFlightApplications()
        );

        assertThatThrownBy(() -> service(client).reconfigure(engine.getId(), request))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).contains("未包含在当前 Admin 的监听配置");
                });

        ComputeEngine current = repository.findById(engine.getId()).orElseThrow();
        assertThat(current.getRegistrationState()).isEqualTo(ComputeEngineRegistrationState.ACTIVE);
        assertThat(current.getAdminEventTopic()).isEqualTo("admin.events");
        assertThat(client.calls).isEmpty();
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
                "engine-" + suffix, "before", "http://127.0.0.1:18092",
                cipher.encrypt("old-secret"), ComputeBackendType.LOCAL_DOCKER,
                "commands." + suffix, "runner." + suffix, "admin.events", 20, 2, 2
        );
        engine.activate("dispatcher-old", ComputeBackendType.LOCAL_DOCKER);
        return repository.saveAndFlush(engine);
    }

    private UpdateComputeEngineRequest changedRequest() {
        String suffix = UUID.randomUUID().toString();
        return new UpdateComputeEngineRequest(
                "updated-" + suffix, "after", "http://127.0.0.1:28092", "new-secret",
                ComputeBackendType.LOCAL_DOCKER, "commands." + suffix + ".next",
                "runner." + suffix + ".next", "admin.events", 30, 3, 4
        );
    }

    private static DispatcherRegistrationResponse registration(
            UUID engineId,
            String dispatcherInstanceId,
            DispatcherRegistrationState state,
            DispatcherTopics topics,
            DispatcherAdmissionPolicy policy
    ) {
        return new DispatcherRegistrationResponse(
                engineId, dispatcherInstanceId, ComputeBackendType.LOCAL_DOCKER,
                state, topics, policy, null
        );
    }

    private static final class StubDispatcherClient extends ComputeEngineDispatcherClient {
        private final UUID engineId;
        private final DispatcherTopics oldTopics;
        private final DispatcherAdmissionPolicy oldPolicy;
        private final List<String> calls = new ArrayList<>();
        private UUID remoteEngineId;
        private DispatcherRegistrationState remoteState;
        private boolean deactivationConflict;
        private boolean infoFailure;
        private boolean activationFailure;
        private boolean remoteConfigurationMismatch;

        private StubDispatcherClient(ComputeEngine engine) {
            super(new ComputeEngineProperties(null, Duration.ofSeconds(1), Duration.ofSeconds(1)));
            engineId = engine.getId();
            oldTopics = new DispatcherTopics(
                    engine.getCommandTopic(), engine.getRunnerEventTopic(), engine.getAdminEventTopic()
            );
            oldPolicy = new DispatcherAdmissionPolicy(
                    engine.getMaxQueuedExecutions(), engine.getMaxConcurrentSubmissions(),
                    engine.getMaxInFlightApplications()
            );
            remoteEngineId = engineId;
            remoteState = engine.getRegistrationState() == ComputeEngineRegistrationState.DRAINING
                    ? DispatcherRegistrationState.DRAINING
                    : DispatcherRegistrationState.ACTIVE;
        }

        @Override
        public DispatcherInfoResponse info(String baseUrl, String token) {
            calls.add("info");
            if (infoFailure) throw new IllegalStateException("candidate unavailable");
            return new DispatcherInfoResponse(
                    baseUrl.contains("28092") ? "dispatcher-next" : "dispatcher-old",
                    ComputeBackendType.LOCAL_DOCKER, "test",
                    new DispatcherCapabilities(true, true, true), List.of()
            );
        }

        @Override
        public DispatcherRegistrationResponse registration(String baseUrl, String token) {
            calls.add("registration");
            return ComputeEngineReconfigurationIntegrationTests.registration(
                    remoteEngineId, "dispatcher-old", remoteState, oldTopics,
                    remoteConfigurationMismatch
                            ? new DispatcherAdmissionPolicy(
                                    oldPolicy.maxQueuedExecutions() + 1,
                                    oldPolicy.maxConcurrentSubmissions(),
                                    oldPolicy.maxInFlightApplications())
                            : oldPolicy
            );
        }

        @Override
        public DispatcherRegistrationResponse drain(String baseUrl, String token) {
            calls.add("drain");
            remoteState = DispatcherRegistrationState.DRAINING;
            return ComputeEngineReconfigurationIntegrationTests.registration(engineId, "dispatcher-old",
                    DispatcherRegistrationState.DRAINING, oldTopics, oldPolicy);
        }

        @Override
        public DispatcherRegistrationResponse deactivate(String baseUrl, String token, boolean force) {
            calls.add("deactivate");
            if (deactivationConflict) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Dispatcher 仍有排队或活动执行");
            }
            remoteState = DispatcherRegistrationState.INACTIVE;
            return ComputeEngineReconfigurationIntegrationTests.registration(engineId, "dispatcher-old",
                    DispatcherRegistrationState.INACTIVE, oldTopics, oldPolicy);
        }

        @Override
        public DispatcherRegistrationResponse activate(
                String baseUrl,
                String token,
                DispatcherRegistrationRequest request
        ) {
            calls.add("activate");
            if (activationFailure) throw new IllegalStateException("activation failed");
            return ComputeEngineReconfigurationIntegrationTests.registration(request.engineId(), "dispatcher-next",
                    DispatcherRegistrationState.ACTIVE, request.topics(), request.admissionPolicy());
        }
    }
}
