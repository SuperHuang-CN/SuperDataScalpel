package cn.superhuang.data.scalpel.dispatcher.backend.kubernetes;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendExecutionState;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionIdentity;
import cn.superhuang.data.scalpel.dispatcher.config.KubernetesProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesBackendContractTest {
    private final ExecutionIdentity identity = new ExecutionIdentity(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            UUID.fromString("33333333-3333-3333-3333-333333333333"), 1);

    @Test
    void derivesStableNamesAndFixedClusterSubmitCommand() {
        KubernetesCommandFactory factory = new KubernetesCommandFactory(properties());
        List<String> command = factory.submit(identity, cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy.defaultsFor(cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType.KUBERNETES).defaults());

        assertThat(KubernetesNames.driverPod(identity))
                .isEqualTo("ds-22222222222222222222222222222222-driver");
        assertThat(command).containsSubsequence("--master", "k8s://https://cluster.example", "--deploy-mode", "cluster");
        assertThat(command).contains("spark.kubernetes.driver.pod.name=" + KubernetesNames.driverPod(identity));
        assertThat(command).contains(
                "spark.kubernetes.driverEnv.DATASCALPEL_TASK_LAUNCH_FILE=/opt/datascalpel/runtime/launch.json");
        assertThat(command).contains(
                "spark.kubernetes.driverEnv.DATASCALPEL_TASK_WORK_DIRECTORY=/tmp/datascalpel");
        assertThat(command.getLast()).isEqualTo("local:///opt/datascalpel/task-runner-cluster.jar");
        assertThat(String.join(" ", command)).doesNotContain("https://minio", "password", "manifestGetUrl");
    }

    @Test
    void parsesPodJsonAndValidatesIdentityLabels() throws Exception {
        KubernetesPodParser parser = new KubernetesPodParser(new ObjectMapper());
        KubernetesPodParser.ParsedPod pod = parser.parse("""
                {
                  "metadata": {
                    "name": "ds-22222222222222222222222222222222-driver",
                    "labels": {
                      "cn.superhuang.datascalpel/managed": "true",
                      "cn.superhuang.datascalpel/engine-id": "11111111-1111-1111-1111-111111111111",
                      "cn.superhuang.datascalpel/execution-id": "22222222-2222-2222-2222-222222222222",
                      "cn.superhuang.datascalpel/run-id": "33333333-3333-3333-3333-333333333333",
                      "cn.superhuang.datascalpel/attempt": "1"
                    }
                  },
                  "status": {
                    "phase": "Succeeded",
                    "startTime": "2026-07-17T12:00:00Z",
                    "containerStatuses": [{"state":{"terminated":{"finishedAt":"2026-07-17T12:01:00Z"}}}]
                  }
                }
                """);
        parser.requireIdentity(pod, identity);
        assertThat(pod.status().state()).isEqualTo(BackendExecutionState.SUCCEEDED);
        assertThat(pod.status().endedAt()).isNotNull();
    }

    @Test
    void secretCommandUsesFilePathAndNeverEmbedsLaunchBody() {
        List<String> command = new KubernetesCommandFactory(properties())
                .createSecret(identity, Path.of("/secure/launch.json"));
        assertThat(command).contains("--from-file=launch.json=/secure/launch.json");
        assertThat(String.join(" ", command)).doesNotContain("manifestGetUrl", "password", "presigned");
    }

    @Test
    void buildsNamespaceAndRbacReadinessCommands() {
        KubernetesCommandFactory factory = new KubernetesCommandFactory(properties());

        assertThat(factory.namespace())
                .containsExactly("kubectl", "-n", "datascalpel", "get", "namespace", "datascalpel", "-o", "name");
        assertThat(factory.authCanI("get", "pods/log"))
                .containsExactly("kubectl", "-n", "datascalpel", "auth", "can-i", "get", "pods/log");
        assertThat(factory.authCanI("delete", "configmaps"))
                .containsExactly("kubectl", "-n", "datascalpel", "auth", "can-i", "delete", "configmaps");
    }

    private static KubernetesProperties properties() {
        return new KubernetesProperties(
                "spark-submit", "kubectl", "k8s://https://cluster.example", "datascalpel", "spark-runner",
                "registry.example/datascalpel/runner@sha256:" + "a".repeat(64),
                "2g", "3g", 2, 3, Duration.ofMinutes(5), Duration.ofMinutes(2), 10,
                Path.of("target/kubernetes-test"));
    }
}
