package cn.superhuang.data.scalpel.dispatcher.service;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendReadiness;
import cn.superhuang.data.scalpel.dispatcher.backend.DispatcherBackendRegistry;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherTargetsProperties;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherRegistrationRepository;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DispatcherEngineSchedulerTest {
    private final DispatcherRegistrationRepository registrations = mock(DispatcherRegistrationRepository.class);
    private final DispatcherExecutionCoordinator coordinator = mock(DispatcherExecutionCoordinator.class);
    private final DispatcherBackendRegistry backends = mock(DispatcherBackendRegistry.class);
    private final DispatcherTaskExecutionRepository executions = mock(DispatcherTaskExecutionRepository.class);
    private final DispatcherRegistration slow = registration("slow");
    private final DispatcherRegistration fast = registration("fast");

    @Test void blockedSubmissionDoesNotBlockOtherEngineObservationOrCleanup() throws Exception {
        prepare();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return null;
        })
                .when(coordinator).admit(slow.getEngineId());
        try (var scheduler = fixture()) {
            scheduler.admit();
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            verify(coordinator, timeout(2000)).admit(fast.getEngineId());
            for (int i = 0; i < 10; i++) scheduler.admit();
            verify(coordinator, times(1)).admit(slow.getEngineId());
            scheduler.observe();
            scheduler.cleanup();
            for (var row : List.of(slow, fast)) {
                verify(coordinator, timeout(2000)).observe(row.getEngineId());
                verify(coordinator, timeout(2000)).cleanup(row.getEngineId());
            }
        } finally { release.countDown(); }
    }

    @Test void unavailableTargetDoesNotClaimItsQueueButOtherTargetStillRuns() throws Exception {
        prepare();
        var unhealthy = backends.require("slow");
        var checked = new CountDownLatch(1);
        when(backends.readiness(unhealthy)).thenAnswer(call -> {
            checked.countDown(); return BackendReadiness.down("unavailable");
        });
        try (var scheduler = fixture()) {
            scheduler.admit();
            assertThat(checked.await(2, TimeUnit.SECONDS)).isTrue();
            verify(coordinator, timeout(2000)).admit(fast.getEngineId());
            verify(coordinator, never()).admit(slow.getEngineId());
        }
    }

    private void prepare() {
        when(registrations.findAll()).thenReturn(List.of(slow, fast));
        when(executions.existsByEngineIdAndStateIn(any(), any())).thenReturn(true);
        when(backends.readiness(any())).thenReturn(BackendReadiness.up());
        for (var row : List.of(slow, fast)) {
            when(backends.require(row.getTargetKey())).thenReturn(new DispatcherBackendRegistry.Target(
                    row.getTargetKey(), row.getTargetKey(), row.getTargetFingerprint(), null, null));
        }
    }

    private SchedulerHandle fixture() {
        return new SchedulerHandle(new DispatcherEngineScheduler(registrations, coordinator, backends,
                new DispatcherTargetsProperties(Map.of(), 2, 4), executions));
    }

    private static DispatcherRegistration registration(String key) {
        var row = DispatcherRegistration.activate(UUID.randomUUID(), UUID.randomUUID(), ExecutionBackendType.LOCAL_DOCKER,
                key + ".command", key + ".runner", "admin.shared", key + ".control", 10, 1, 2,
                SparkExecutionResourcePolicy.defaultsFor(ExecutionBackendType.LOCAL_DOCKER));
        row.bindTarget(key, key.equals("slow") ? "a".repeat(64) : "b".repeat(64));
        return row;
    }

    private record SchedulerHandle(DispatcherEngineScheduler scheduler) implements AutoCloseable {
        void admit() { scheduler.admit(); }
        void observe() { scheduler.observe(); }
        void cleanup() { scheduler.cleanup(); }
        @Override public void close() { scheduler.close(); }
    }
}
