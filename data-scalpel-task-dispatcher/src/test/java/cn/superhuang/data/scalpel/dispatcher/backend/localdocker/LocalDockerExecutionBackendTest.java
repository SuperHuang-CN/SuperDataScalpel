package cn.superhuang.data.scalpel.dispatcher.backend.localdocker;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.contract.execution.RunnerEventChannel;
import cn.superhuang.data.scalpel.contract.execution.RunnerKafkaSecurityProtocol;
import cn.superhuang.data.scalpel.contract.execution.RunnerSparkMode;
import cn.superhuang.data.scalpel.contract.execution.TaskExecutionLaunchDescriptor;
import cn.superhuang.data.scalpel.dispatcher.artifact.ArtifactLaunchAccess;
import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherArtifactService;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendExecutionState;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendReadiness;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendSubmission;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionIdentity;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionLaunch;
import cn.superhuang.data.scalpel.dispatcher.backend.ExternalExecutionHandle;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherProperties;
import cn.superhuang.data.scalpel.dispatcher.config.LocalDockerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDockerExecutionBackendTest {
    private static final String CONTAINER_ID = "a".repeat(64);

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsStartsAndPersistsRecoverableContainerIdentity() throws Exception {
        int[] nameLookups = {0};
        ScriptedDockerCli cli = new ScriptedDockerCli(command -> {
            if (isPs(command)) {
                nameLookups[0]++;
                return nameLookups[0] == 1 ? success("") : success("\"" + CONTAINER_ID + "\"\n");
            }
            if (verb(command, "create")) {
                return success("output-is-not-used", "WARNING: image platform does not match host platform");
            }
            if (verb(command, "inspect")) {
                return success(DockerInspectParserTest.inspectionJson("created", false, 0, identity()));
            }
            if (verb(command, "start")) return success(CONTAINER_ID + "\n");
            throw new AssertionError("Unexpected command: " + command);
        });
        LocalDockerExecutionBackend backend = backend(cli);

        BackendSubmission submission = backend.submit(launch());

        assertThat(submission.handle().externalId()).isEqualTo(CONTAINER_ID);
        assertThat(cli.commands).anySatisfy(command -> assertThat(command).contains("create", "--env-file"));
        assertThat(cli.commands).anySatisfy(command -> assertThat(command).contains("start", CONTAINER_ID));
        Path work = temporaryDirectory.resolve("work").resolve(identity().executionId().toString()).resolve("attempt-1");
        assertThat(work.resolve("launch.json")).isRegularFile();
        assertThat(work.resolve("runner.env")).isRegularFile();
        TaskExecutionLaunchDescriptor descriptor = new ObjectMapper().readValue(
                work.resolve("launch.json").toFile(), TaskExecutionLaunchDescriptor.class);
        assertThat(descriptor.launchVersion()).isEqualTo(TaskExecutionLaunchDescriptor.CURRENT_VERSION);
        assertThat(descriptor.engineId()).isEqualTo(identity().engineId());
        assertThat(descriptor.executionId()).isEqualTo(identity().executionId());
        assertThat(descriptor.runId()).isEqualTo(identity().runId());
        assertThat(descriptor.attempt()).isEqualTo(identity().attempt());
        assertThat(descriptor.sparkMode()).isEqualTo(RunnerSparkMode.LOCAL);
        assertThat(descriptor.manifest().getUrl()).isEqualTo(URI.create("http://minio/manifest"));
        assertThat(descriptor.result().putUrl()).isEqualTo(URI.create("http://minio/result"));
        assertThat(descriptor.runnerEvent()).isEqualTo(launch().runnerEvent());
        String env = Files.readString(work.resolve("runner.env"));
        assertThat(env).contains("DATASCALPEL_TASK_LAUNCH_FILE=/work/launch.json")
                .contains("DATASCALPEL_TASK_WORK_DIRECTORY=/work")
                .doesNotContain("DATASCALPEL_TASK_MANIFEST_URL");
    }

    @Test
    void keepsCreatedHandleRecoverableWhenDockerStartFails() throws Exception {
        int[] nameLookups = {0};
        ScriptedDockerCli cli = new ScriptedDockerCli(command -> {
            if (isPs(command)) {
                nameLookups[0]++;
                return nameLookups[0] == 1 ? success("") : success("\"" + CONTAINER_ID + "\"\n");
            }
            if (verb(command, "create")) return success(CONTAINER_ID);
            if (verb(command, "inspect")) {
                return success(DockerInspectParserTest.inspectionJson("created", false, 0, identity()));
            }
            if (verb(command, "start")) return failure();
            throw new AssertionError("Unexpected command: " + command);
        });

        BackendSubmission submission = backend(cli).submit(launch());

        assertThat(submission.handle()).isEqualTo(handle());
        assertThat(cli.commands).anySatisfy(command -> assertThat(command).contains("start", CONTAINER_ID));
    }

    @Test
    void failsWithRecoveryErrorWhenCreateSucceedsButContainerCannotBeFound() throws Exception {
        ScriptedDockerCli cli = new ScriptedDockerCli(command -> {
            if (isPs(command)) return success("");
            if (verb(command, "create")) return success("unexpected output");
            throw new AssertionError("Unexpected command: " + command);
        });

        assertThatThrownBy(() -> backend(cli).submit(launch()))
                .isInstanceOf(BackendException.class)
                .extracting(error -> ((BackendException) error).code())
                .isEqualTo("DOCKER_CONTAINER_RECOVERY_FAILED");
    }

    @Test
    void reportsImageAndServerPlatformMismatchDuringReadiness() throws Exception {
        ScriptedDockerCli cli = new ScriptedDockerCli(command -> {
            if (verb(command, "version")) return success("27.4.0");
            if (verb(command, "info")) return success("linux/aarch64");
            if (verb(command, "image")) return success("linux/amd64");
            throw new AssertionError("Unexpected command: " + command);
        });

        BackendReadiness readiness = backend(cli).readiness();

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.issues())
                .contains("Runner 镜像平台 linux/amd64 与期望平台 linux/arm64 不一致");
    }

    @Test
    void mapsDockerInspectStatesWithoutStringHeuristics() throws Exception {
        ScriptedDockerCli cli = inspectingCli("running", true, 0);
        LocalDockerExecutionBackend backend = backend(cli);
        ExternalExecutionHandle handle = handle();

        assertThat(backend.inspect(handle).state()).isEqualTo(BackendExecutionState.RUNNING);

        cli.handler = inspectionHandler("exited", false, 0);
        assertThat(backend.inspect(handle).state()).isEqualTo(BackendExecutionState.SUCCEEDED);

        cli.handler = inspectionHandler("exited", false, 17);
        assertThat(backend.inspect(handle).state()).isEqualTo(BackendExecutionState.FAILED);
        assertThat(backend.inspect(handle).safeErrorCode()).isEqualTo("CONTAINER_EXIT_NON_ZERO");

        cli.handler = inspectionHandler("dead", false, 137);
        assertThat(backend.inspect(handle).state()).isEqualTo(BackendExecutionState.FAILED);
    }

    @Test
    void cancellationStopsThenKillsContainerStillRunning() throws Exception {
        ScriptedDockerCli cli = new ScriptedDockerCli(command -> {
            if (isPs(command)) return success("\"" + CONTAINER_ID + "\"\n");
            if (verb(command, "inspect")) {
                return success(DockerInspectParserTest.inspectionJson("running", true, 0, identity()));
            }
            if (verb(command, "stop")) return failure();
            if (verb(command, "kill")) return success(CONTAINER_ID);
            throw new AssertionError("Unexpected command: " + command);
        });
        LocalDockerExecutionBackend backend = backend(cli);

        backend.cancel(handle());

        assertThat(cli.commands.stream().map(command -> command.get(1)).toList())
                .containsSubsequence("ps", "inspect", "stop", "ps", "inspect", "kill");
    }

    @Test
    void cancellationRecoversContainerByExecutionLabelsWhenStoredHandleIsMissing() throws Exception {
        int[] idLookups = {0};
        ScriptedDockerCli cli = new ScriptedDockerCli(command -> {
            if (isPs(command)) {
                String text = String.join(" ", command);
                if (text.contains("label=" + DockerCommandFactory.EXECUTION_ID_LABEL)) {
                    return success("\"" + CONTAINER_ID + "\"\n");
                }
                idLookups[0]++;
                return idLookups[0] == 2 ? success("\"" + CONTAINER_ID + "\"\n") : success("");
            }
            if (verb(command, "inspect")) {
                return success(DockerInspectParserTest.inspectionJson("running", true, 0, identity()));
            }
            if (verb(command, "stop")) return success(CONTAINER_ID);
            throw new AssertionError("Unexpected command: " + command);
        });
        LocalDockerExecutionBackend backend = backend(cli);

        backend.cancel(handle(), identity());

        assertThat(cli.commands).anySatisfy(command -> assertThat(String.join(" ", command))
                .contains("label=" + DockerCommandFactory.EXECUTION_ID_LABEL + "=" + identity().executionId()));
        assertThat(cli.commands).anySatisfy(command -> assertThat(command).contains("stop", CONTAINER_ID));
    }

    @Test
    void recoversCreatedContainerByLabelsAndStartsItOnce() throws Exception {
        ScriptedDockerCli cli = new ScriptedDockerCli(command -> {
            if (isPs(command)) return success("\"" + CONTAINER_ID + "\"\n");
            if (verb(command, "inspect")) {
                return success(DockerInspectParserTest.inspectionJson("created", false, 0, identity()));
            }
            if (verb(command, "start")) return success(CONTAINER_ID);
            throw new AssertionError("Unexpected command: " + command);
        });
        LocalDockerExecutionBackend backend = backend(cli);

        assertThat(backend.recover(identity())).contains(handle());
        assertThat(cli.commands).anySatisfy(command -> assertThat(command).contains("start", CONTAINER_ID));
    }

    @Test
    void refusesAmbiguousRecoveryInsteadOfPickingOrDeletingContainer() throws Exception {
        String second = "b".repeat(64);
        ScriptedDockerCli cli = new ScriptedDockerCli(command -> success(
                "\"" + CONTAINER_ID + "\"\n\"" + second + "\"\n"));
        LocalDockerExecutionBackend backend = backend(cli);

        assertThatThrownBy(() -> backend.recover(identity()))
                .isInstanceOf(BackendException.class)
                .extracting(error -> ((BackendException) error).code())
                .isEqualTo("AMBIGUOUS_EXTERNAL_EXECUTION");
        assertThat(cli.commands).noneSatisfy(command -> assertThat(command).containsAnyOf("rm", "kill"));
    }

    private LocalDockerExecutionBackend backend(ScriptedDockerCli cli) throws Exception {
        Path runnerJar = temporaryDirectory.resolve("task-runner.jar");
        if (!Files.exists(runnerJar)) Files.writeString(runnerJar, "test");
        LocalDockerProperties local = new LocalDockerProperties(
                "docker", "eclipse-temurin:21-jdk", null, "missing", "4g", "2", runnerJar,
                temporaryDirectory.resolve("work"), Duration.ofSeconds(30), Duration.ofSeconds(10), "-Xmx3g");
        ObjectMapper objectMapper = new ObjectMapper();
        return new LocalDockerExecutionBackend(
                cli,
                new DockerCommandFactory(local),
                new DockerInspectParser(objectMapper),
                new LocalDockerWorkspaceService(local, objectMapper),
                new StubArtifactService(),
                local,
                new DispatcherProperties("token", ExecutionBackendType.LOCAL_DOCKER,
                        Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofMillis(500), Duration.ofMinutes(1),
                        Duration.ofMinutes(2), Duration.ofMinutes(5), Duration.ofSeconds(30),
                        Duration.ofSeconds(10), DataSize.ofMegabytes(20))
        );
    }

    private static ScriptedDockerCli inspectingCli(String state, boolean running, int exitCode) {
        return new ScriptedDockerCli(inspectionHandler(state, running, exitCode));
    }

    private static Function<List<String>, DockerCommandResult> inspectionHandler(
            String state,
            boolean running,
            int exitCode
    ) {
        return command -> {
            if (isPs(command)) return success("\"" + CONTAINER_ID + "\"\n");
            if (verb(command, "inspect")) {
                return success(DockerInspectParserTest.inspectionJson(state, running, exitCode, identity()));
            }
            throw new AssertionError("Unexpected command: " + command);
        };
    }

    private static ExecutionLaunch launch() {
        String prefix = "task-runs/" + identity().runId() + "/attempts/1/";
        return new ExecutionLaunch(identity(), prefix + "manifest.json", "1".repeat(64),
                prefix + "result.json", prefix + "console.log",
                Instant.now().plusSeconds(3600), new RunnerEventChannel(
                        "kafka:9092", "datascalpel.runner.local", RunnerKafkaSecurityProtocol.PLAINTEXT,
                        "runner-" + identity().executionId()));
    }

    private static ExecutionIdentity identity() { return DockerInspectParserTest.identity(); }

    private static ExternalExecutionHandle handle() {
        return new ExternalExecutionHandle(ExecutionBackendType.LOCAL_DOCKER, CONTAINER_ID, null);
    }

    private static boolean isPs(List<String> command) { return verb(command, "ps"); }
    private static boolean verb(List<String> command, String verb) { return command.size() > 1 && verb.equals(command.get(1)); }
    private static DockerCommandResult success(String output) {
        return success(output, "");
    }

    private static DockerCommandResult success(String stdout, String stderr) {
        return new DockerCommandResult(0, stdout.getBytes(), stderr.getBytes(), false, false);
    }

    private static DockerCommandResult failure() {
        return new DockerCommandResult(1, new byte[0], new byte[0], false, false);
    }

    private static final class ScriptedDockerCli implements DockerCli {
        private final List<List<String>> commands = new ArrayList<>();
        private Function<List<String>, DockerCommandResult> handler;

        private ScriptedDockerCli(Function<List<String>, DockerCommandResult> handler) { this.handler = handler; }

        @Override
        public DockerCommandResult execute(List<String> arguments, Duration timeout, long maximumOutputBytes) {
            commands.add(List.copyOf(arguments));
            return handler.apply(arguments);
        }
    }

    private static final class StubArtifactService implements DispatcherArtifactService {
        @Override public BackendReadiness readiness() { return BackendReadiness.up(); }

        @Override
        public ArtifactLaunchAccess prepareLaunch(ExecutionLaunch launch) {
            return new ArtifactLaunchAccess(
                    URI.create("http://minio/manifest"),
                    URI.create("http://minio/result"),
                    URI.create("http://minio/log"), 10 * 1024 * 1024);
        }

        @Override public Optional<byte[]> readIfPresent(String objectKey, int maximumBytes) { return Optional.empty(); }
        @Override public void store(String objectKey, byte[] content, String contentType) { }
    }
}
