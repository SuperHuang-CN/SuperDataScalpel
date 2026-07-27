package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.LaunchArtifactDownload;
import cn.superhuang.data.scalpel.contract.execution.LaunchArtifactUpload;
import cn.superhuang.data.scalpel.contract.execution.RunnerEventChannel;
import cn.superhuang.data.scalpel.contract.execution.RunnerExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerKafkaSecurityProtocol;
import cn.superhuang.data.scalpel.contract.execution.RunnerResultAvailableEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerSparkMode;
import cn.superhuang.data.scalpel.contract.execution.RunnerStartedEvent;
import cn.superhuang.data.scalpel.contract.execution.TaskExecutionLaunchDescriptor;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionResult;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionState;
import cn.superhuang.datascalpel.taskengine.http.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRunnerApplicationTest {
    private static final ObjectMapper OBJECT_MAPPER = JsonSupport.strictObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void uploadsResultBeforePublishingResultAvailable() throws Exception {
        Fixture fixture = fixture(false);
        List<RunnerExecutionEvent> events = new ArrayList<>();
        FakeArtifactAccess artifacts = new FakeArtifactAccess(fixture.manifestBytes());
        RunnerEventPublisher publisher = publisher(events, artifacts);
        RunnerTaskExecutor executor = (manifest, mode, started) -> {
            assertEquals(RunnerSparkMode.LOCAL, mode);
            started.accept("local-test");
            Instant now = Instant.now();
            return new TaskExecutionResult(TaskExecutionResult.CURRENT_SCHEMA_VERSION,
                    fixture.executionId(), fixture.runId(), 1,
                    TaskExecutionState.SUCCESS, now, now.plusMillis(1), 1L, 7L, List.of(), null);
        };
        TaskRunnerApplication application = new TaskRunnerApplication(
                OBJECT_MAPPER, artifacts, ignored -> publisher, executor);

        Path workDirectory = temporaryDirectory.resolve("runner-work");
        int code = application.run(Map.of(
                "DATASCALPEL_TASK_LAUNCH_FILE", fixture.launchFile().toString(),
                "DATASCALPEL_TASK_WORK_DIRECTORY", workDirectory.toString()));

        assertEquals(0, code);
        assertTrue(artifacts.uploaded);
        assertTrue(Files.isRegularFile(workDirectory.resolve("result.json")));
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof RunnerStartedEvent);
        assertTrue(events.get(1) instanceof RunnerResultAvailableEvent);
        RunnerResultAvailableEvent available = (RunnerResultAvailableEvent) events.get(1);
        assertEquals(sha256(artifacts.uploadedContent), available.resultSha256());
    }

    @Test
    void digestMismatchProducesAndUploadsSafeFailedResultWithoutExecutingTask() throws Exception {
        Fixture fixture = fixture(true);
        FakeArtifactAccess artifacts = new FakeArtifactAccess(fixture.manifestBytes());
        List<RunnerExecutionEvent> events = new ArrayList<>();
        TaskRunnerApplication application = new TaskRunnerApplication(
                OBJECT_MAPPER,
                artifacts,
                ignored -> publisher(events, artifacts),
                (manifest, mode, started) -> { throw new AssertionError("executor must not run"); });

        int code = application.run(Map.of("DATASCALPEL_TASK_LAUNCH_FILE", fixture.launchFile().toString()));

        assertEquals(1, code);
        assertTrue(artifacts.uploaded);
        TaskExecutionResult result = OBJECT_MAPPER.readValue(artifacts.uploadedContent, TaskExecutionResult.class);
        assertEquals(2, result.schemaVersion());
        assertEquals(TaskExecutionState.FAILED, result.state());
        assertEquals("MANIFEST_DIGEST_MISMATCH", result.error().code());
        assertTrue(events.getLast() instanceof RunnerResultAvailableEvent);
    }

    @Test
    void rejectsManifestV3BeforeExecutingTask() throws Exception {
        Fixture fixture = fixture(false, 3);
        FakeArtifactAccess artifacts = new FakeArtifactAccess(fixture.manifestBytes());
        TaskRunnerApplication application = new TaskRunnerApplication(
                OBJECT_MAPPER,
                artifacts,
                ignored -> publisher(new ArrayList<>(), artifacts),
                (manifest, mode, started) -> {
                    throw new AssertionError("v3 manifest must not execute");
                }
        );

        int code = application.run(Map.of(
                "DATASCALPEL_TASK_LAUNCH_FILE", fixture.launchFile().toString()));

        assertEquals(1, code);
        TaskExecutionResult result = OBJECT_MAPPER.readValue(
                artifacts.uploadedContent,
                TaskExecutionResult.class
        );
        assertEquals("INVALID_MANIFEST", result.error().code());
    }

    @Test
    void invalidLaunchIsRejectedWithoutArtifactOrKafkaAccess() throws Exception {
        Path launch = temporaryDirectory.resolve("launch.json");
        Files.writeString(launch, "{\"launchVersion\":1,\"unexpected\":true}");
        FakeArtifactAccess artifacts = new FakeArtifactAccess(new byte[0]);
        TaskRunnerApplication application = new TaskRunnerApplication(
                OBJECT_MAPPER, artifacts, ignored -> { throw new AssertionError(); },
                (manifest, mode, started) -> { throw new AssertionError(); });

        int code = application.run(Map.of("DATASCALPEL_TASK_LAUNCH_FILE", launch.toString()));

        assertEquals(2, code);
        assertFalse(artifacts.uploaded);
    }

    @Test
    void mapsInterruptedExecutionToCancelledResult() {
        UUID executionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        TaskExecutionResult result = CanvasTaskExecutor.failure(
                executionId, runId, 1, Instant.now().minusMillis(1),
                new RunnerExecutionException("EXECUTION_CANCELLED", "任务执行已取消", null));

        assertEquals(TaskExecutionState.CANCELLED, result.state());
        assertEquals("EXECUTION_CANCELLED", result.error().code());
    }

    private Fixture fixture(boolean wrongDigest) throws Exception {
        return fixture(wrongDigest, TaskExecutionManifest.CURRENT_MANIFEST_VERSION);
    }

    private Fixture fixture(boolean wrongDigest, int manifestVersion) throws Exception {
        UUID engineId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID executionId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        UUID runId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        Instant deadline = Instant.now().plusSeconds(300).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        String manifestJson = """
                {
                  "manifestVersion":%d,
                  "execution":{
                    "executionId":"%s","runId":"%s","taskId":"40000000-0000-0000-0000-000000000004",
                    "attempt":1,"definitionVersion":1,"createdAt":"%s","deadlineAt":"%s"
                  },
                  "task":null,"metadataSnapshot":null,"runtimeDataSources":[]
                }
                """.formatted(manifestVersion, executionId, runId, Instant.now(), deadline);
        byte[] manifest = manifestJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest = wrongDigest ? "0".repeat(64) : sha256(manifest);
        TaskExecutionLaunchDescriptor descriptor = new TaskExecutionLaunchDescriptor(
                2, engineId, executionId, runId, 1, deadline, RunnerSparkMode.LOCAL,
                new LaunchArtifactDownload(URI.create("http://minio/manifest"), digest, 1024 * 1024),
                new LaunchArtifactUpload(URI.create("http://minio/result"),
                        "task-runs/" + runId + "/attempts/1/result.json"),
                new RunnerEventChannel("kafka:9092", "datascalpel.runner.test",
                        RunnerKafkaSecurityProtocol.PLAINTEXT, "runner-" + executionId));
        Path launch = temporaryDirectory.resolve("launch.json");
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(launch.toFile(), descriptor);
        return new Fixture(executionId, runId, launch, manifest);
    }

    private static RunnerEventPublisher publisher(
            List<RunnerExecutionEvent> events,
            FakeArtifactAccess artifacts
    ) {
        return new RunnerEventPublisher() {
            @Override
            public void publish(RunnerExecutionEvent event) {
                if (event instanceof RunnerResultAvailableEvent && !artifacts.uploaded) {
                    throw new AssertionError("result event was published before artifact upload");
                }
                events.add(event);
            }
            @Override public void close() { }
        };
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private record Fixture(UUID executionId, UUID runId, Path launchFile, byte[] manifestBytes) { }

    private static final class FakeArtifactAccess implements RunnerArtifactAccess {
        private final byte[] manifest;
        private boolean uploaded;
        private byte[] uploadedContent;

        private FakeArtifactAccess(byte[] manifest) { this.manifest = manifest; }

        @Override public byte[] download(URI uri, int maximumBytes) { return manifest.clone(); }

        @Override
        public void upload(URI uri, byte[] content, String contentType) {
            uploaded = true;
            uploadedContent = content.clone();
        }
    }
}
