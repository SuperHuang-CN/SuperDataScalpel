package cn.superhuang.data.scalpel.dispatcher;

import cn.superhuang.data.scalpel.contract.execution.*;
import cn.superhuang.data.scalpel.dispatcher.backend.*;
import cn.superhuang.data.scalpel.dispatcher.config.*;
import cn.superhuang.data.scalpel.dispatcher.management.*;
import cn.superhuang.data.scalpel.dispatcher.messaging.MessageCoordinates;
import cn.superhuang.data.scalpel.dispatcher.messaging.command.DispatcherCommandService;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import cn.superhuang.data.scalpel.dispatcher.service.DispatcherExecutionStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {"data-scalpel.dispatcher.messaging.scope=INSTANCE",
        "data-scalpel.dispatcher.messaging.command-topic=commands.shared",
        "data-scalpel.dispatcher.messaging.runner-event-topic=runner.shared",
        "data-scalpel.dispatcher.messaging.admin-event-topic=admin.shared"})
@Transactional
class DispatcherMultiTargetIntegrationTest {
    @MockitoBean DispatcherBackendRegistry backends;
    @Autowired DispatcherRegistrationService registration;
    @Autowired DispatcherRuntimeService runtime;
    @Autowired DispatcherIdentityService identity;
    @Autowired DispatcherCommandService commands;
    @Autowired DispatcherExecutionStateService states;
    @Autowired DispatcherTaskExecutionRepository executions;
    @Autowired cn.superhuang.data.scalpel.dispatcher.repository.DispatcherEventOutboxRepository outbox;
    @Autowired DispatcherListenerManager listeners;
    @Autowired LocalDockerProperties docker;
    @Autowired KubernetesProperties kubernetes;
    @Autowired jakarta.persistence.EntityManager entityManager;
    private final UUID localId = UUID.randomUUID();
    private final UUID kubeId = UUID.randomUUID();
    private DispatcherBackendRegistry.Target local;
    private DispatcherBackendRegistry.Target kube;

    @BeforeEach void setup() {
        local = target("local", ExecutionBackendType.LOCAL_DOCKER, "a");
        kube = target("kube", ExecutionBackendType.KUBERNETES, "b");
        when(backends.legacy()).thenReturn(false);
        when(backends.all()).thenReturn(List.of(local, kube));
        when(backends.require("local")).thenReturn(local);
        when(backends.require("kube")).thenReturn(kube);
        when(backends.readiness(any())).thenReturn(BackendReadiness.up());
    }

    @Test void isolatesQueuesAdmissionSnapshotsAndDraining() {
        registration.activate(request(localId, local));
        registration.activate(request(kubeId, kube));
        var a = submit(localId);
        var b = submit(kubeId);
        assertThat(accept(a, 1)).isEqualTo(DispatcherCommandService.Outcome.ACCEPTED);
        assertThat(accept(submit(localId), 2)).isEqualTo(DispatcherCommandService.Outcome.REJECTED);
        assertThat(accept(b, 1)).isEqualTo(DispatcherCommandService.Outcome.ACCEPTED);
        entityManager.flush(); entityManager.clear();
        var savedA = executions.findByExecutionId(a.executionId()).orElseThrow();
        var savedB = executions.findByExecutionId(b.executionId()).orElseThrow();
        assertThat(savedA.getTargetKey()).isEqualTo("local");
        assertThat(savedB.getTargetFingerprint()).isEqualTo("b".repeat(64));
        assertThat(states.claimNext(localId)).isPresent();
        assertThat(states.claimNext(kubeId)).isPresent();
        registration.drain(localId);
        assertThat(registration.current(kubeId).state().name()).isEqualTo("ACTIVE");
        assertThat(listeners.listenersRunning(kubeId)).isTrue();
        assertThat(registration.targets().targets()).hasSize(2);
        assertThatThrownBy(registration::info).isInstanceOf(ResponseStatusException.class);
    }

    @Test void resumesOriginalQueueWithoutChangingOtherEngineOrTopics() {
        registration.activate(request(localId, local));
        registration.activate(request(kubeId, kube));
        var queued = submit(localId);
        accept(queued, 1);
        var before = registration.current(localId);
        registration.drain(localId);
        assertThat(states.claimNext(localId)).isEmpty();
        registration.resume(localId);
        assertThat(registration.current(localId).topics()).isEqualTo(before.topics());
        assertThat(registration.current(localId).state().name()).isEqualTo("ACTIVE");
        assertThat(registration.current(kubeId).state().name()).isEqualTo("ACTIVE");
        assertThat(states.claimNext(localId)).isPresent();
        assertThatThrownBy(() -> registration.resume(localId)).isInstanceOf(ResponseStatusException.class);
        registration.drain(kubeId);
        when(backends.readiness(kube)).thenReturn(new BackendReadiness(false, List.of("unavailable")));
        assertThatThrownBy(() -> registration.resume(kubeId)).isInstanceOf(ResponseStatusException.class);
        assertThat(registration.current(kubeId).state().name()).isEqualTo("DRAINING");
    }

    @Test void forceDeactivationOnlyCancelsSelectedEngine() {
        registration.activate(request(localId, local));
        registration.activate(request(kubeId, kube));
        var a = submit(localId); var b = submit(kubeId);
        accept(a, 1); accept(b, 1);
        registration.deactivate(localId, true);
        assertThat(executions.findByExecutionId(a.executionId()).orElseThrow().getState()).isEqualTo(DispatcherExecutionState.CANCELLED);
        assertThat(executions.findByExecutionId(b.executionId()).orElseThrow().getState()).isEqualTo(DispatcherExecutionState.QUEUED);
        assertThat(listeners.listenersRunning(localId)).isTrue(); // Instance consumers remain available for the other engine.
        assertThat(listeners.listenersRunning(kubeId)).isTrue();
        assertThat(outbox.findAll().stream().filter(event -> event.getExecutionId().equals(a.executionId())
                && event.getMessageType().equals("EXECUTION_CANCELLED")).toList()).hasSize(1);
        assertThat(outbox.findAll().stream().filter(event -> event.getExecutionId().equals(b.executionId())
                && event.getMessageType().equals("EXECUTION_CANCELLED")).toList()).isEmpty();
        registration.deactivate(localId, true);
        assertThat(outbox.findAll().stream().filter(event -> event.getExecutionId().equals(a.executionId())
                && event.getMessageType().equals("EXECUTION_CANCELLED")).toList()).hasSize(1);
    }

    @Test void rejectsOccupiedTargetsDifferentCommandTopicsAndStaleDiscovery() {
        registration.activate(request(localId, local));
        assertThatThrownBy(() -> registration.activate(request(UUID.randomUUID(), local))).isInstanceOf(ResponseStatusException.class);
        var good = request(kubeId, kube);
        var wrong = new DispatcherRegistrationRequest(kubeId, new DispatcherTopics("wrong.commands", "wrong.runner", "admin.shared"), good.admissionPolicy(),
                good.resourcePolicy(), good.targetKey(), good.dispatcherInstanceId(), good.expectedBackendType(), good.targetFingerprint());
        assertThatThrownBy(() -> registration.activate(wrong)).isInstanceOf(ResponseStatusException.class);
        var stale = new DispatcherRegistrationRequest(kubeId, good.topics(), good.admissionPolicy(), good.resourcePolicy(),
                good.targetKey(), good.dispatcherInstanceId(), good.expectedBackendType(), "c".repeat(64));
        assertThatThrownBy(() -> registration.activate(stale)).isInstanceOf(ResponseStatusException.class);
        registration.activate(good);
        assertThat(registration.current(localId).state().name()).isEqualTo("ACTIVE");
    }

    @Test void changedPhysicalTargetNeverClaimsExistingExecution() {
        registration.activate(request(localId, local));
        var command = submit(localId); accept(command, 1);
        var changed = target("local", ExecutionBackendType.LOCAL_DOCKER, "c");
        when(backends.require("local")).thenReturn(changed);
        assertThat(states.claimNext(localId)).isEmpty();
        assertThat(executions.findByExecutionId(command.executionId()).orElseThrow().getState()).isEqualTo(DispatcherExecutionState.QUEUED);
    }

    @Test void discoversDeploymentPolicyAndRejectsAdminOverride() {
        assertThat(registration.targets().targets()).allSatisfy(target ->
                assertThat(target.resourcePolicy()).isEqualTo(backends.require(target.targetKey()).configuration().resourcePolicy()));
        var good = request(localId, local);
        var changed = new SparkExecutionResourcePolicy(new SparkExecutionResourceSpec(3, 4096, 1, 1, 1024), good.resourcePolicy().maximums());
        var bad = new DispatcherRegistrationRequest(localId, good.topics(), good.admissionPolicy(), changed,
                good.targetKey(), good.dispatcherInstanceId(), good.expectedBackendType(), good.targetFingerprint());
        assertThatThrownBy(() -> registration.activate(bad)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("资源策略与 Dispatcher 部署配置不一致");
    }

    @Test void appliesTaskResourcesAndChecksCurrentDeploymentMaximums() {
        registration.activate(request(localId, local));
        var c = submit(localId);
        var custom = new SparkExecutionResourceSpec(4, 8192, 20, 8, 16384);
        var command = new SubmitExecutionCommand(c.messageVersion(), c.messageId(), c.messageType(), c.occurredAt(),
                c.engineId(), c.executionId(), c.runId(), c.attempt(), c.taskId(), c.taskType(), c.definitionVersion(),
                c.deadlineAt(), c.artifacts(), List.of(), 0, null, List.of(), custom);
        assertThat(accept(command, 1)).isEqualTo(DispatcherCommandService.Outcome.ACCEPTED);
        assertThat(executions.findByExecutionId(c.executionId()).orElseThrow().getExecutionResources()).isEqualTo(custom.forBackend(ExecutionBackendType.LOCAL_DOCKER));
        var lower = new SparkExecutionResourcePolicy(new SparkExecutionResourceSpec(1, 1024, 1, 1, 1024),
                new SparkExecutionResourceSpec(1, 1024, 1, 1, 1024));
        var config = local.configuration();
        when(backends.require("local")).thenReturn(new DispatcherBackendRegistry.Target(local.key(), local.name(),
                local.fingerprint(), local.backend(), new DispatcherTargetsProperties.Target(true, config.name(),
                config.backend(), config.localDocker(), config.yarn(), config.kubernetes(), config.streaming(), lower)));
        var next = submit(localId);
        var overviewResources = runtime.overview(localId).resourceConfiguration();
        assertThat(overviewResources.containerMemoryLimit()).isEqualTo("1024m");
        assertThat(overviewResources.containerCpuLimit()).isEqualTo("1");
        assertThat(overviewResources.runnerJvmHeap()).isEqualTo("-Xmx768m");
        assertThat(overviewResources.executorInstances()).isNull();
        var over = new SubmitExecutionCommand(next.messageVersion(), next.messageId(), next.messageType(), next.occurredAt(),
                next.engineId(), next.executionId(), next.runId(), next.attempt(), next.taskId(), next.taskType(), next.definitionVersion(),
                next.deadlineAt(), next.artifacts(), List.of(), 0, null, List.of(), custom);
        assertThat(accept(over, 2)).isEqualTo(DispatcherCommandService.Outcome.REJECTED);
        assertThat(executions.findByExecutionId(c.executionId()).orElseThrow().getExecutionResources()).isEqualTo(custom.forBackend(ExecutionBackendType.LOCAL_DOCKER));
    }

    private DispatcherBackendRegistry.Target target(String key, ExecutionBackendType type, String fingerprint) {
        var backend = mock(TaskExecutionBackend.class);
        when(backend.type()).thenReturn(type);
        return new DispatcherBackendRegistry.Target(key, key, fingerprint.repeat(64), backend,
                new DispatcherTargetsProperties.Target(true, key, type, docker, null, kubernetes, null));
    }
    private DispatcherRegistrationRequest request(UUID engine, DispatcherBackendRegistry.Target target) {
        return new DispatcherRegistrationRequest(engine, new DispatcherTopics("commands.shared", "runner.shared", "admin.shared"),
                new DispatcherAdmissionPolicy(1, 1, 1), SparkExecutionResourcePolicy.defaultsFor(target.backend().type()),
                target.key(), identity.instanceId().toString(), target.backend().type(), target.fingerprint());
    }
    private DispatcherCommandService.Outcome accept(SubmitExecutionCommand command, long offset) {
        return commands.accept(command, new MessageCoordinates("commands.shared", 0, offset));
    }
    private SubmitExecutionCommand submit(UUID engine) {
        UUID run = UUID.randomUUID(); String prefix = "task-runs/" + run + "/attempts/1/";
        return new SubmitExecutionCommand(1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION, Instant.now(), engine,
                UUID.randomUUID(), run, 1, UUID.randomUUID(), ExecutionTaskType.SPARK_CANVAS, 1, Instant.now().plusSeconds(3600),
                new ExecutionArtifactLocation(prefix + "manifest.json", "a".repeat(64), prefix + "result.json", prefix + "console.log"));
    }
}
