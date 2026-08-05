package cn.superhuang.data.scalpel.dispatcher.backend.localdocker;

import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionIdentity;
import cn.superhuang.data.scalpel.dispatcher.config.LocalDockerProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DockerCommandFactoryTest {

    @Test
    void buildsStableCreateArgumentsWithoutShell() {
        LocalDockerProperties properties = properties();
        DockerCommandFactory factory = new DockerCommandFactory(properties);
        ExecutionIdentity identity = new ExecutionIdentity(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                UUID.fromString("30000000-0000-0000-0000-000000000003"), 1);

        List<String> command = factory.create(identity, Path.of("/tmp/execution"), Path.of("/tmp/execution/runner.env"));

        assertThat(command.getFirst()).isEqualTo("docker-custom");
        assertThat(command).containsSubsequence("create", "--name", "datascalpel-runner-" + identity.executionId());
        assertThat(command).contains("--platform", "linux/arm64", "--pull", "missing", "--memory", "4g", "--cpus", "2");
        assertThat(command).contains(
                "cn.superhuang.datascalpel.managed=true",
                "cn.superhuang.datascalpel.engine-id=" + identity.engineId(),
                "cn.superhuang.datascalpel.execution-id=" + identity.executionId(),
                "cn.superhuang.datascalpel.run-id=" + identity.runId(),
                "cn.superhuang.datascalpel.attempt=1");
        assertThat(command).doesNotContain("sh", "bash", "-c", "--rm", "run");
        assertThat(command).containsSubsequence(
                "eclipse-temurin:21-jdk", "java",
                "-XX:+IgnoreUnrecognizedVMOptions",
                "--add-modules=jdk.incubator.vector",
                "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
                "--add-opens=java.base/java.nio=ALL-UNNAMED",
                "--enable-native-access=ALL-UNNAMED",
                "-jar", "/opt/datascalpel/task-runner.jar"
        );
        assertThat(command).endsWith(
                "--enable-native-access=ALL-UNNAMED", "-jar", "/opt/datascalpel/task-runner.jar");
    }

    private static LocalDockerProperties properties() {
        return new LocalDockerProperties(
                "docker-custom", "eclipse-temurin:21-jdk", "linux/arm64", "missing", "4g", "2",
                Path.of("/tmp/task-runner.jar"), Path.of("/tmp/work"),
                Duration.ofSeconds(30), Duration.ofSeconds(10), "-Xmx3g");
    }
}
