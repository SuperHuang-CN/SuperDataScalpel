package cn.superhuang.data.scalpel.dispatcher.backend.yarn;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendExecutionState;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionIdentity;
import cn.superhuang.data.scalpel.dispatcher.config.YarnProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YarnBackendContractTest {
    private final ExecutionIdentity identity = new ExecutionIdentity(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            UUID.fromString("33333333-3333-3333-3333-333333333333"), 1);

    @Test
    void buildsFixedClusterSubmitCommandWithoutLaunchContent() {
        YarnCommandFactory factory = new YarnCommandFactory(properties());
        List<String> command = factory.submit(identity, Path.of("/secure/launch.json"), cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy.defaultsFor(cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType.YARN).defaults());

        assertThat(command).containsSubsequence("--master", "yarn", "--deploy-mode", "cluster");
        assertThat(command).contains("spark.yarn.submit.waitAppCompletion=false");
        assertThat(command).contains("spark.yarn.tags=datascalpel-execution-" + identity.executionId());
        assertThat(command.getLast()).isEqualTo("hdfs:///runner.jar");
        assertThat(String.join(" ", command)).doesNotContain("https://", "password", "manifestSha256");
    }

    @Test
    void parsesApplicationIdentityAndTerminalState() throws Exception {
        YarnStatusParser parser = new YarnStatusParser();
        assertThat(parser.uniqueApplicationId("submitted application_1700000000000_0042"))
                .isEqualTo("application_1700000000000_0042");
        YarnStatusParser.ParsedStatus status = parser.parseStatus("""
                Application-Id : application_1700000000000_0042
                State : FINISHED
                Final-State : SUCCEEDED
                Start-Time : 1700000000000
                Finish-Time : 1700000005000
                Tracking-URL : https://rm.example/application_1700000000000_0042
                """);
        assertThat(status.status().state()).isEqualTo(BackendExecutionState.SUCCEEDED);
        assertThat(status.status().trackingUrl()).startsWith("https://rm.example/");
    }

    @Test
    void rejectsAmbiguousApplicationIds() {
        YarnStatusParser parser = new YarnStatusParser();
        assertThatThrownBy(() -> parser.uniqueApplicationId(
                "application_1700000000000_0001 application_1700000000000_0002"))
                .hasMessageContaining("多个");
    }

    @Test
    void buildsReadinessCommandsForSparkYarnHdfsAndJava21() {
        YarnCommandFactory factory = new YarnCommandFactory(properties());

        assertThat(factory.version()).containsExactly("spark-submit", "--version");
        assertThat(factory.nodes()).containsExactly("yarn", "node", "-list");
        assertThat(factory.runnerJarReadable())
                .containsExactly("hdfs", "dfs", "-test", "-r", "hdfs:///runner.jar");
        assertThat(factory.javaVersion()).containsExactly("java", "-version");
    }

    private static YarnProperties properties() {
        return new YarnProperties("spark-submit", "yarn", "hdfs", "cluster", "etl",
                "hdfs:///runner.jar", "2g", "3g", 2, 3,
                Duration.ofMinutes(5), Duration.ofMinutes(2), Path.of("target/yarn-test"));
    }
}
