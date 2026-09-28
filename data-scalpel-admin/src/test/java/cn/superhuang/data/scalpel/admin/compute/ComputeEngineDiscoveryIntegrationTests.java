package cn.superhuang.data.scalpel.admin.compute;

import cn.superhuang.data.scalpel.business.compute.client.*;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineRegistrationState;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineDiscoveryService;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineManagementService;
import cn.superhuang.data.scalpel.business.compute.web.request.*;
import cn.superhuang.data.scalpel.contract.execution.DispatcherTargetDirectoryResponse;
import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;
import cn.superhuang.data.scalpel.business.task.web.request.*;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ComputeEngineDiscoveryIntegrationTests {
    @Autowired ComputeEngineDiscoveryService discovery;
    @Autowired ComputeEngineManagementService management;
    @Autowired ComputeEngineRepository repository;
    @Autowired cn.superhuang.data.scalpel.business.task.service.DataTaskService tasks;
    @Autowired cn.superhuang.data.scalpel.business.task.service.SparkJarTaskDefinitionService jarDefinitions;
    @MockitoBean ComputeEngineDispatcherClient client;
    @MockitoBean cn.superhuang.data.scalpel.business.task.service.TaskRunArtifactStorage artifactStorage;
    // Access-catalog startup uses a PostgreSQL table lock, unrelated to this H2 service regression.
    @MockitoBean(name = "initializeSystemAccess") org.springframework.boot.ApplicationRunner accessInitializer;
    private final String instance = UUID.randomUUID().toString();
    private final Map<String, DispatcherRegistrationResponse> remote = new HashMap<>();
    private boolean failKube;

    @BeforeEach void setup() {
        when(client.targets(anyString(), anyString())).thenAnswer(call -> directory());
        when(client.targetInfo(anyString(), anyString(), anyString())).thenAnswer(call -> {
            String key = call.getArgument(2);
            return new DispatcherInfoResponse(instance, type(key), "test", new DispatcherCapabilities(true, true, true), List.of());
        });
        when(client.activate(anyString(), anyString(), any())).thenAnswer(call -> {
            DispatcherRegistrationRequest request = call.getArgument(2);
            if (failKube && request.targetKey().equals("kube")) throw new IllegalStateException("simulated upstream failure");
            var response = new DispatcherRegistrationResponse(request.engineId(), instance, type(request.targetKey()),
                    DispatcherRegistrationState.ACTIVE, request.topics(), request.admissionPolicy(), request.resourcePolicy(),
                    null, request.targetKey(), request.targetFingerprint());
            remote.put(request.targetKey(), response);
            return response;
        });
        when(client.registration(anyString(), anyString(), any(UUID.class))).thenAnswer(call -> remote.values().stream()
                .filter(r -> r.engineId().equals(call.getArgument(2))).findFirst().orElseThrow());
        when(client.drain(anyString(), anyString(), any(UUID.class))).thenAnswer(call -> {
            var old = remote.values().stream().filter(r -> r.engineId().equals(call.getArgument(2))).findFirst().orElseThrow();
            var changed = new DispatcherRegistrationResponse(old.engineId(), instance, old.backendType(), DispatcherRegistrationState.DRAINING,
                    old.topics(), old.effectiveAdmissionPolicy(), old.resourcePolicy(), null, old.targetKey(), old.targetFingerprint());
            remote.put(old.targetKey(), changed); return changed;
        });
    }

    @Test void discoversWithoutSavingAndCreatesSeparateEnginesForOneAddress() {
        long before = repository.count();
        assertThat(discovery.discover(new DiscoverComputeTargetsRequest("http://dispatcher.test", "token")).targets()).hasSize(2);
        assertThat(repository.count()).isEqualTo(before);
        var result = discovery.register(request("http://dispatcher.test", "local", "kube"));
        assertThat(result.items()).allMatch(item -> item.success());
        assertThat(result.items()).extracting(item -> item.engine().id()).doesNotHaveDuplicates();
        assertThat(result.items()).extracting(item -> item.engine().commandTopic()).containsOnly("commands." + instance);
        assertThat(result.items()).allMatch(item -> item.engine().registrationState() == ComputeEngineRegistrationState.ACTIVE);
        var retried = discovery.register(request("http://address-alias.test", "local", "kube"));
        assertThat(retried.items()).allMatch(item -> item.success());
        assertThat(repository.count()).isEqualTo(before + 2);
        verify(client, times(2)).activate(anyString(), anyString(), any());
    }

    @Test void retainsSuccessAndReusesFailedRecordOnRetry() {
        failKube = true;
        var result = discovery.register(request("http://dispatcher.test", "local", "kube"));
        assertThat(result.items().getFirst().success()).isTrue();
        var failed = result.items().get(1);
        assertThat(failed.success()).isFalse();
        assertThat(failed.engine().registrationState()).isEqualTo(ComputeEngineRegistrationState.ERROR);
        failKube = false;
        var retry = discovery.register(request("http://dispatcher.test", "kube")).items().getFirst();
        assertThat(retry.success()).isTrue();
        assertThat(retry.engine().id()).isEqualTo(failed.engine().id());
    }

    @Test void scopesDrainAndPreventsDeletingActiveOrUncertainRegistration() {
        var result = discovery.register(request("http://dispatcher.test", "local", "kube"));
        var local = result.items().getFirst().engine();
        management.drain(local.id());
        assertThat(remote.get("local").state()).isEqualTo(DispatcherRegistrationState.DRAINING);
        assertThat(remote.get("kube").state()).isEqualTo(DispatcherRegistrationState.ACTIVE);
        assertThatThrownBy(() -> management.delete(local.id())).isInstanceOf(ResponseStatusException.class);
        verify(client, never()).drain(anyString(), anyString());
    }

    @Test void resumesOnlyPausedEngineAndPreservesTopics() {
        var local = discovery.register(request("http://dispatcher.test", "local", "kube")).items().getFirst().engine();
        assertThatThrownBy(() -> management.resume(local.id())).isInstanceOf(ResponseStatusException.class);
        management.drain(local.id());
        when(client.resume(anyString(), anyString(), eq(local.id()))).thenAnswer(call -> {
            var old = remote.get("local");
            var active = new DispatcherRegistrationResponse(old.engineId(), instance, old.backendType(), DispatcherRegistrationState.ACTIVE,
                    old.topics(), old.effectiveAdmissionPolicy(), old.resourcePolicy(), null, old.targetKey(), old.targetFingerprint());
            remote.put("local", active); return active;
        });
        var resumed = management.resume(local.id());
        assertThat(resumed.registrationState()).isEqualTo(ComputeEngineRegistrationState.ACTIVE);
        assertThat(resumed.commandTopic()).isEqualTo(local.commandTopic());
        assertThat(remote.get("kube").state()).isEqualTo(DispatcherRegistrationState.ACTIVE);
        verify(client).resume(anyString(), anyString(), eq(local.id()));
    }

    @Test void preservesStopConflictWithoutExposingUpstreamBodyOrMarkingUnhealthy() {
        var local = discovery.register(request("http://dispatcher.test", "local")).items().getFirst().engine();
        when(client.deactivate(anyString(), anyString(), eq(local.id()), anyBoolean())).thenThrow(
                org.springframework.web.client.HttpClientErrorException.create(
                        org.springframework.http.HttpStatus.CONFLICT, "Conflict", org.springframework.http.HttpHeaders.EMPTY,
                        "private-upstream-response".getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8));
        for (boolean force : List.of(false, true)) {
            assertThatThrownBy(() -> management.deactivate(local.id(), force))
                    .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                        assertThat(error.getStatusCode().value()).isEqualTo(409);
                        assertThat(error.getReason()).contains("暂不能停用引擎").doesNotContain("private-upstream-response");
                    });
        }
        var unchanged = repository.findById(local.id()).orElseThrow();
        assertThat(unchanged.getRegistrationState()).isEqualTo(ComputeEngineRegistrationState.ACTIVE);
        assertThat(unchanged.getHealthState()).isEqualTo(cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineHealthState.UP);
    }

    @Test void rejectsChangedInstanceBeforeCreatingAnything() {
        var request = request("http://dispatcher.test", "local");
        var stale = new RegisterComputeTargetsRequest(request.dispatcherBaseUrl(), request.accessToken(), "old-instance", request.targets());
        long before = repository.count();
        assertThatThrownBy(() -> discovery.register(stale)).isInstanceOf(ResponseStatusException.class);
        assertThat(repository.count()).isEqualTo(before);
    }

    private RegisterComputeTargetsRequest request(String url, String... keys) {
        return new RegisterComputeTargetsRequest(url, "token", instance, Arrays.stream(keys).map(key ->
                new RegisterComputeTargetsRequest.Target(key, fingerprint(key), instance + "-" + key, 20, 2, 2, null)).toList());
    }

    @Test void ignoresBrowserPolicyAndSavesTaskSpecificResources() {
        var input = request("http://dispatcher.test", "local");
        var old = input.targets().getFirst();
        var tampered = new RegisterComputeTargetsRequest.Target(old.targetKey(), old.targetFingerprint(), old.name(),
                20, 2, 2, SparkExecutionResourcePolicy.defaultsFor(ExecutionBackendType.KUBERNETES));
        var engine = discovery.register(new RegisterComputeTargetsRequest(input.dispatcherBaseUrl(), input.accessToken(),
                instance, List.of(tampered))).items().getFirst().engine();
        assertThat(engine.resourcePolicy()).isEqualTo(SparkExecutionResourcePolicy.defaultsFor(ExecutionBackendType.LOCAL_DOCKER));
        var custom = new SparkExecutionResourceSpec(4, 8192, 1, 1, 1024);
        var task = tasks.create(new CreateDataTaskRequest("resources", null, TaskType.SPARK_CANVAS, null, engine.id(), custom));
        assertThat(task.executionResources()).isEqualTo(custom);
        assertThat(tasks.update(task.id(), new UpdateDataTaskRequest(task.name(), null, null, engine.id(), null))
                .executionResources()).isNull();
        assertThatThrownBy(() -> tasks.update(task.id(), new UpdateDataTaskRequest(task.name(), null, null, engine.id(),
                new SparkExecutionResourceSpec(9, 8192, 1, 1, 1024))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("单次任务上限");
    }

    @Test void jarCanSwitchFromCustomToInheritedWithoutFreezingDefaults() {
        var engine = discovery.register(request("http://dispatcher.test", "local")).items().getFirst().engine();
        var task = tasks.create(new CreateDataTaskRequest("jar resources", null, TaskType.SPARK_JAR, null, engine.id()));
        var custom = new UpdateSparkJarTaskDefinitionRequest.ExecutionResources(4, 8192, 1, 1, 1024);
        var saved = jarDefinitions.update(task.id(), new UpdateSparkJarTaskDefinitionRequest(List.of(), List.of(), List.of(), custom, 3600));
        assertThat(saved.inheritEngineResources()).isFalse();
        assertThat(saved.executionResources().driverCores()).isEqualTo(4);
        var inherited = jarDefinitions.update(task.id(), new UpdateSparkJarTaskDefinitionRequest(
                List.of(), List.of(), List.of(), null, 3600, null, null, true));
        assertThat(inherited.inheritEngineResources()).isTrue();
        assertThat(inherited.executionResources()).isEqualTo(engine.resourcePolicy().defaults());
    }
    private DispatcherTargetDirectoryResponse directory() {
        return new DispatcherTargetDirectoryResponse(instance, 3,
                new DispatcherTargetDirectoryResponse.Messaging("commands." + instance, "runner." + instance,
                        "datascalpel.execution.event", "runner." + instance + ".control"),
                List.of("local", "kube").stream().map(key -> {
            var registered = remote.get(key);
            return new DispatcherTargetDirectoryResponse.Target(key, key, ExecutionBackendType.valueOf(type(key).name()), fingerprint(key), true,
                    List.of(), registered == null ? null : registered.engineId(), registered == null ? "UNREGISTERED" : registered.state().name(),
                    new DispatcherTargetDirectoryResponse.Capabilities(true, true, true, false, false),
                    SparkExecutionResourcePolicy.defaultsFor(ExecutionBackendType.valueOf(type(key).name())));
        }).toList());
    }
    private static ComputeBackendType type(String key) { return key.equals("local") ? ComputeBackendType.LOCAL_DOCKER : ComputeBackendType.KUBERNETES; }
    private static String fingerprint(String key) { return (key.equals("local") ? "a" : "b").repeat(64); }
}
