package cn.superhuang.data.scalpel.dispatcher.backend.command;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessBuilderCommandExecutorTest {
    private static final String VARIABLE = "DATASCALPEL_TEST_TARGET_ENVIRONMENT";

    @Test
    void isolatesConcurrentTargetEnvironmentsWithoutMutatingParent() throws Exception {
        String inherited = System.getenv(VARIABLE);
        var executor = new ProcessBuilderCommandExecutor();
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = threads.submit(() -> executor.execute(command(), Duration.ofSeconds(10), 1024,
                    Map.of(VARIABLE, "cluster-a")));
            var second = threads.submit(() -> executor.execute(command(), Duration.ofSeconds(10), 1024,
                    Map.of(VARIABLE, "cluster-b")));
            assertThat(first.get().outputText()).isEqualTo("cluster-a");
            assertThat(second.get().outputText()).isEqualTo("cluster-b");
        }
        var unconfigured = executor.execute(command(), Duration.ofSeconds(10), 1024);
        assertThat(unconfigured.successful()).isTrue();
        assertThat(unconfigured.outputText()).isEqualTo(inherited == null ? "unset" : inherited);
        assertThat(System.getenv(VARIABLE)).isEqualTo(inherited);
    }

    private List<String> command() {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        if (!Files.isRegularFile(java)) java = java.resolveSibling("java.exe");
        return List.of(java.toString(), "-cp", System.getProperty("java.class.path"), EnvironmentProbe.class.getName());
    }

    public static class EnvironmentProbe {
        public static void main(String[] args) {
            System.out.print(System.getenv().getOrDefault(VARIABLE, "unset"));
        }
    }
}
