package cn.superhuang.data.scalpel.admin.compute;

import cn.superhuang.data.scalpel.business.compute.client.ComputeEngineDispatcherClient;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherAdmissionPolicy;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherCapabilities;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherInfoResponse;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherRegistrationResponse;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherRegistrationState;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherTopics;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngine;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineCredentialCipher;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ComputeEngineExecutionIntegrationTests {

    @Autowired ComputeEngineRepository repository;
    @Autowired ComputeEngineCredentialCipher cipher;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void rejectsRunWhenRemoteRegistrationIsNoLongerActive() {
        ComputeEngine engine = ComputeEngine.create(
                "engine-" + UUID.randomUUID(), null, "http://127.0.0.1:18092",
                cipher.encrypt("secret"), ComputeBackendType.LOCAL_DOCKER,
                "commands.active", "runner.active", "admin.events", 20, 2, 2);
        engine.activate("dispatcher-1", 1, ComputeBackendType.LOCAL_DOCKER);
        repository.saveAndFlush(engine);

        StubDispatcherClient dispatcherClient = new StubDispatcherClient();
        dispatcherClient.info = new DispatcherInfoResponse(
                1, "dispatcher-1", ComputeBackendType.LOCAL_DOCKER, "test",
                new DispatcherCapabilities(true, true, true), List.of());
        dispatcherClient.registration = registration(engine, DispatcherRegistrationState.DRAINING);
        ComputeEngineExecutionService executionService = new ComputeEngineExecutionService(
                repository, cipher, dispatcherClient, transactionManager);

        assertThatThrownBy(() -> executionService.requireRunnable(engine.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("远端注册状态");
                });

        dispatcherClient.registration = registration(engine, DispatcherRegistrationState.ACTIVE);
        assertThat(executionService.requireRunnable(engine.getId()).engineId()).isEqualTo(engine.getId());
    }

    private static DispatcherRegistrationResponse registration(
            ComputeEngine engine,
            DispatcherRegistrationState state
    ) {
        return new DispatcherRegistrationResponse(
                1, engine.getId(), "dispatcher-1", ComputeBackendType.LOCAL_DOCKER,
                engine.getConfigRevision(), state,
                new DispatcherTopics(engine.getCommandTopic(), engine.getRunnerEventTopic(), engine.getAdminEventTopic()),
                new DispatcherAdmissionPolicy(
                        engine.getMaxQueuedExecutions(), engine.getMaxConcurrentSubmissions(),
                        engine.getMaxInFlightApplications()), null);
    }

    private static final class StubDispatcherClient extends ComputeEngineDispatcherClient {
        private DispatcherInfoResponse info;
        private DispatcherRegistrationResponse registration;

        private StubDispatcherClient() {
            super(new ComputeEngineProperties(null, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        }

        @Override public DispatcherInfoResponse info(String baseUrl, String token) { return info; }
        @Override public DispatcherRegistrationResponse registration(String baseUrl, String token) {
            return registration;
        }
    }
}
