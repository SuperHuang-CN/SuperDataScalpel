package cn.superhuang.data.scalpel.dispatcher.backend.kubernetes;

import cn.superhuang.data.scalpel.contract.execution.*;
import cn.superhuang.data.scalpel.dispatcher.artifact.*;
import cn.superhuang.data.scalpel.dispatcher.backend.*;
import cn.superhuang.data.scalpel.dispatcher.backend.cluster.ClusterLaunchFileService;
import cn.superhuang.data.scalpel.dispatcher.backend.command.*;
import cn.superhuang.data.scalpel.dispatcher.config.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KubernetesSecretLifecycleTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutionIdentity identity = new ExecutionIdentity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1);

    @Test
    void createsLabelledSecretAtomicallyAndRemovesTemporaryDocument() throws Exception {
        Path[] document = {null};
        boolean[] submitted = {false};
        CommandExecutor executor = (command, timeout, limit) -> {
            if (command.contains("get") && command.contains("pod")) {
                return submitted[0] ? result(0, pod()) : result(1, "NotFound");
            }
            if (command.contains("get") && command.contains("secret")) return result(1, "NotFound");
            if (command.contains("create")) {
                document[0] = Path.of(command.get(command.indexOf("-f") + 1));
                var secret = mapper.readTree(document[0].toFile());
                assertThat(secret.path("kind").asText()).isEqualTo("Secret");
                new KubernetesPodParser(mapper).requireSecretIdentity(secret.toString(), identity);
                assertThat(secret.path("metadata").path("namespace").asText()).isEqualTo("datascalpel-test");
                assertThat(new String(Base64.getDecoder().decode(secret.path("data").path("launch.json").asText()),
                        StandardCharsets.UTF_8)).contains("manifest");
                return result(0, "created");
            }
            if (command.getFirst().equals("spark-submit")) {
                submitted[0] = true;
                return result(0, "submitted");
            }
            throw new AssertionError("Unexpected command " + command);
        };
        var backend = backend(executor);
        String prefix = "task-runs/" + identity.runId() + "/attempts/1/";
        var launch = new ExecutionLaunch(identity, prefix + "manifest.json", "a".repeat(64),
                prefix + "result.json", prefix + "execution.log", null,
                new RunnerEventChannel("kafka:9092", "test.runner", RunnerKafkaSecurityProtocol.PLAINTEXT, "test-runner"));
        assertThat(backend.submit(launch).handle().externalId()).isEqualTo(KubernetesNames.driverPod(identity));
        assertThat(document[0]).isNotNull();
        assertThat(document[0]).doesNotExist();
    }

    @Test
    void cleanupDoesNotDeleteAnUnownedSecret() throws Exception {
        List<List<String>> commands = new ArrayList<>();
        var backend = backend((command, timeout, limit) -> {
            commands.add(command);
            return result(0, "{\"metadata\":{\"name\":\"unowned\"}}");
        });
        assertThatThrownBy(() -> backend.cleanup(identity)).isInstanceOf(BackendException.class)
                .hasMessageContaining("身份不匹配");
        assertThat(commands).allSatisfy(command -> assertThat(command).doesNotContain("delete"));
    }

    @Test
    void cleanupOfMissingSecretIsIdempotent() throws Exception {
        var backend = backend((command, timeout, limit) -> {
            assertThat(command).contains("get").doesNotContain("delete");
            return result(1, "NotFound");
        });
        backend.cleanup(identity);
    }

    @Test
    void passesExplicitKubeconfigToChildProcessWithoutChangingApiServer() throws Exception {
        Path kubeconfig = directory.resolve("dispatcher.kubeconfig");
        var executor = new CommandExecutor() {
            @Override
            public CommandResult execute(List<String> command, Duration timeout, long limit) {
                throw new AssertionError("Explicit kubeconfig must be passed to the child process");
            }

            @Override
            public CommandResult execute(List<String> command, Duration timeout, long limit,
                                         Map<String, String> environment) {
                assertThat(environment).containsExactly(entry("KUBECONFIG", kubeconfig.toAbsolutePath().toString()));
                assertThat(command).contains("--server=https://test", "get", "secret");
                return result(1, "NotFound");
            }
        };
        backend(executor, kubeconfig).cleanup(identity);
    }

    @Test
    void readinessRejectsMissingConfiguredKubeconfigBeforeExecutingCommands() throws Exception {
        var backend = backend((command, timeout, limit) -> {
            throw new AssertionError("Missing kubeconfig must not fall back to default credentials");
        }, directory.resolve("missing.kubeconfig"));
        assertThat(backend.readiness().ready()).isFalse();
        assertThat(backend.readiness().issues()).contains("Kubernetes kubeconfig 不存在或不可读");
    }

    @ParameterizedTest
    @CsvSource({"30,false", "31,false", "32,true", "33+,true", "unknown,false"})
    void readinessChecksActualKubernetesServerVersion(String minor, boolean ready) throws Exception {
        var backend = backend((command, timeout, limit) -> {
            if (command.contains("--version")) return result(0, "Spark version 4.1.1");
            if (command.contains("--raw=/version")) return result(0, "{\"major\":\"1\",\"minor\":\"" + minor + "\"}");
            if (command.contains("namespace")) return result(0, "namespace/datascalpel-test");
            if (command.contains("can-i")) return result(0, "yes");
            throw new AssertionError("Unexpected command " + command);
        });
        var readiness = backend.readiness();
        assertThat(readiness.ready()).isEqualTo(ready);
        if (!ready) assertThat(readiness.issues()).contains("无法确认 Kubernetes 版本满足 Spark 4.1.1 所需的最低版本 1.32");
    }

    @Test
    void readinessRequiresSparkServerSideApplyPermissionsButNotSecretPatch() throws Exception {
        var backend = backend((command, timeout, limit) -> {
            if (command.contains("--version")) return result(0, "Spark version 4.1.1");
            if (command.contains("--raw=/version")) return result(0, "{\"major\":\"1\",\"minor\":\"32\"}");
            if (command.contains("namespace")) return result(0, "namespace/datascalpel-test");
            if (command.contains("can-i")) {
                if (command.contains("patch")) {
                    assertThat(command.getLast()).isIn("services", "configmaps");
                    return result(1, "no");
                }
                return result(0, "yes");
            }
            throw new AssertionError("Unexpected command " + command);
        });
        var readiness = backend.readiness();
        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.issues()).containsExactlyInAnyOrder(
                "Dispatcher缺少Kubernetes权限: patch services", "Dispatcher缺少Kubernetes权限: patch configmaps");
    }

    private KubernetesSparkExecutionBackend backend(CommandExecutor executor) throws BackendException {
        return backend(executor, null);
    }

    @Test
    void terminalCleanupDeletesCompletedPodAndUsesFullIdentitySelector() throws Exception {
        List<List<String>> commands = new ArrayList<>();
        var backend = backend((command, timeout, limit) -> {
            commands.add(command);
            if (command.contains("get") && command.contains("pod")) return result(0, pod().replace("Pending", "Succeeded"));
            if (command.contains("get") && command.contains("secret")) return result(1, "NotFound");
            return result(0, "deleted");
        });
        backend.cleanup(new ExternalExecutionHandle(ExecutionBackendType.KUBERNETES,
                KubernetesNames.driverPod(identity), null), identity);
        assertThat(commands).anySatisfy(c -> assertThat(c).contains("delete", "pod", KubernetesNames.driverPod(identity)));
        assertThat(commands).anySatisfy(c -> assertThat(c).contains("delete", "pods", KubernetesNames.selector(identity)));
        assertThat(KubernetesNames.selector(identity)).contains(KubernetesNames.RUN_ID + "=" + identity.runId(),
                KubernetesNames.ATTEMPT + "=1");
    }

    @Test
    void terminalCleanupRefusesRunningPodWithoutDeletingAnything() throws Exception {
        var backend = backend((command, timeout, limit) -> {
            assertThat(command).doesNotContain("delete");
            return result(0, pod());
        });
        assertThatThrownBy(() -> backend.cleanup(new ExternalExecutionHandle(ExecutionBackendType.KUBERNETES,
                KubernetesNames.driverPod(identity), null), identity)).isInstanceOf(BackendException.class)
                .hasMessageContaining("尚未终止");
    }

    @Test
    void terminalCleanupRefusesDifferentAttemptWithoutDeletingAnything() throws Exception {
        var backend = backend((command, timeout, limit) -> {
            assertThat(command).doesNotContain("delete");
            return result(0, pod().replace("Pending", "Succeeded").replace("/attempt\":\"1\"", "/attempt\":\"2\""));
        });
        assertThatThrownBy(() -> backend.cleanup(new ExternalExecutionHandle(ExecutionBackendType.KUBERNETES,
                KubernetesNames.driverPod(identity), null), identity)).isInstanceOf(BackendException.class)
                .hasMessageContaining("身份不匹配");
    }

    private KubernetesSparkExecutionBackend backend(CommandExecutor executor, Path kubeconfig) throws BackendException {
        var artifacts = mock(DispatcherArtifactService.class);
        when(artifacts.readiness()).thenReturn(BackendReadiness.up());
        when(artifacts.prepareLaunch(any())).thenReturn(new ArtifactLaunchAccess(
                URI.create("http://minio/manifest"), URI.create("http://minio/result"), URI.create("http://minio/log"), 1024));
        var properties = new KubernetesProperties("spark-submit", "kubectl", "k8s://https://test", "datascalpel-test",
                "spark-runner", "test/runner@sha256:" + "a".repeat(64), null, null, 1, 1, null, null, 10, directory, kubeconfig);
        var dispatcher = new DispatcherProperties("test", ExecutionBackendType.KUBERNETES,
                null, null, null, null, null, null, null, null, null);
        return new KubernetesSparkExecutionBackend(properties, executor, new ClusterLaunchFileService(mapper),
                artifacts, dispatcher, mapper);
    }

    private String pod() {
        return mapper.writeValueAsString(Map.of("metadata", Map.of("name", KubernetesNames.driverPod(identity),
                "labels", Map.of(KubernetesNames.MANAGED, "true", KubernetesNames.ENGINE_ID, identity.engineId().toString(),
                        KubernetesNames.EXECUTION_ID, identity.executionId().toString(), KubernetesNames.RUN_ID, identity.runId().toString(),
                        KubernetesNames.ATTEMPT, "1")), "status", Map.of("phase", "Pending")));
    }

    private static CommandResult result(int code, String body) {
        return new CommandResult(code, body.getBytes(StandardCharsets.UTF_8), false);
    }
}
