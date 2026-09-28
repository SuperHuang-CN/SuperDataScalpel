package cn.superhuang.data.scalpel.dispatcher.backend.localdocker;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        if (!Files.isRegularFile(java)) java = java.resolveSibling("java.exe");
        DockerCommandResult result = new ProcessBuilderDockerCli().execute(
                List.of(java.toString(), "-cp", System.getProperty("java.class.path"), OutputProbe.class.getName()),
                Duration.ofSeconds(5), 1024);

        assertThat(result.successful()).isTrue();
        assertThat(result.stdoutText()).isEqualTo("container-id");
        assertThat(result.stderrText()).isEqualTo("platform warning");
        assertThat(new String(result.combinedOutput(), StandardCharsets.UTF_8))
                .isEqualTo("container-id\nplatform warning");
    }

    public static class OutputProbe {
        public static void main(String[] args) {
            System.out.print("container-id");
            System.err.print("platform warning");
        }
    }
}
