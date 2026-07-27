package cn.superhuang.data.scalpel.dispatcher.backend.localdocker;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessBuilderDockerCliTest {

    @Test
    void keepsHeadAndTailWhenOutputExceedsLimit() {
        ProcessBuilderDockerCli.HeadTailOutput output = new ProcessBuilderDockerCli.HeadTailOutput(1024);
        byte[] first = ("HEAD-" + "a".repeat(1500)).getBytes(StandardCharsets.UTF_8);
        byte[] last = ("b".repeat(1500) + "-TAIL").getBytes(StandardCharsets.UTF_8);

        output.write(first, 0, first.length);
        output.write(last, 0, last.length);
        String result = new String(output.bytes(), StandardCharsets.UTF_8);

        assertThat(output.truncated()).isTrue();
        assertThat(output.bytes()).hasSize(1024);
        assertThat(result).startsWith("HEAD-").contains("DOCKER OUTPUT TRUNCATED").endsWith("-TAIL");
    }

    @Test
    void keepsStdoutAndStderrSeparate() throws Exception {
        DockerCommandResult result = new ProcessBuilderDockerCli().execute(
                List.of("sh", "-c", "printf 'container-id'; printf 'platform warning' >&2"),
                Duration.ofSeconds(5), 1024);

        assertThat(result.successful()).isTrue();
        assertThat(result.stdoutText()).isEqualTo("container-id");
        assertThat(result.stderrText()).isEqualTo("platform warning");
        assertThat(new String(result.combinedOutput(), StandardCharsets.UTF_8))
                .isEqualTo("container-id\nplatform warning");
    }
}
