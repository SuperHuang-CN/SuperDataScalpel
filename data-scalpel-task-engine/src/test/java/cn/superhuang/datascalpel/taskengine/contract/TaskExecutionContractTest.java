package cn.superhuang.datascalpel.taskengine.contract;

import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationRequest;

import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageEnvelope;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.execution.RunnerExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerUserObservabilityEvent;
import cn.superhuang.data.scalpel.contract.execution.UserJobMetricSnapshot;
import cn.superhuang.data.scalpel.contract.execution.UserJobObservabilitySnapshot;
import cn.superhuang.data.scalpel.contract.execution.UserJobStatus;
import cn.superhuang.datascalpel.taskengine.http.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskExecutionContractTest {
    private final ObjectMapper objectMapper = JsonSupport.strictObjectMapper();

    @Test
    void keepsRuntimeConnectionsOutsideTheStableCanvasDefinition() throws Exception {
        TaskCompilationRequest compilation = objectMapper.readValue(
                Files.readString(Path.of("examples/valid-canvas-compilation.json")),
                TaskCompilationRequest.class
        );
        UUID executionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        RuntimeDataSource runtime = new RuntimeDataSource(
                compilation.metadataSnapshot().dataSources().getFirst().id(),
                RuntimeDatabaseType.POSTGRESQL,
                Set.of(DataSourcePurpose.SOURCE),
                new RuntimeJdbcConnection(
                        "org.postgresql.Driver",
                        "jdbc:postgresql://database:5432/example",
                        "example",
                        "public",
                        "runner",
                        "plain-text-password",
                        Map.of("sslmode", "disable")
                )
        );
        TaskExecutionManifest manifest = new TaskExecutionManifest(
                TaskExecutionManifest.CURRENT_MANIFEST_VERSION,
                new TaskExecutionManifest.Execution(
                        executionId, runId, UUID.randomUUID(), 1, 3,
                        Instant.parse("2026-07-17T08:00:00Z"),
                        Instant.parse("2026-07-17T09:00:00Z")
                ),
                compilation.task(),
                compilation.metadataSnapshot(),
                List.of(runtime)
        );

        String json = objectMapper.writeValueAsString(manifest);
        TaskExecutionManifest restored = objectMapper.readValue(json, TaskExecutionManifest.class);

        assertEquals(executionId, restored.execution().executionId());
        assertEquals(compilation.task().definition(), restored.task().definition());
        assertEquals("plain-text-password", restored.runtimeDataSources().getFirst().connection().password());
        String definitionJson = objectMapper.writeValueAsString(restored.task().definition());
        assertFalse(definitionJson.contains("plain-text-password"));
        assertFalse(definitionJson.contains("jdbc:postgresql"));
        assertTrue(json.contains("runtimeDataSources"));
    }

    @Test
    void roundTripsCurrentManifestLogicalFileInputWithOrderedSources() throws Exception {
        UUID tableId = UUID.randomUUID();
        UUID firstSourceId = UUID.randomUUID();
        UUID secondSourceId = UUID.randomUUID();
        RuntimeFileInput input = new RuntimeFileInput(
                UUID.randomUUID(),
                tableId,
                "a".repeat(64),
                new RuntimeFileParsingOptions.JsonLines("UTF-8", FileRecordDelimiter.LF),
                List.of(
                        new RuntimeFileSource(
                                firstSourceId, UUID.randomUUID(),
                                FileDatasetFormat.JSONL, FileDatasetCompression.NONE,
                                FileDatasetStorageKind.SINGLE_OBJECT,
                                "datasets/first.jsonl", null, "FILE"
                        ),
                        new RuntimeFileSource(
                                secondSourceId, UUID.randomUUID(),
                                FileDatasetFormat.JSONL, FileDatasetCompression.GZIP,
                                FileDatasetStorageKind.SINGLE_OBJECT,
                                "datasets/second.jsonl.gz", null, "FILE"
                        )
                )
        );
        TaskExecutionManifest manifest = new TaskExecutionManifest(
                TaskExecutionManifest.CURRENT_MANIFEST_VERSION,
                new TaskExecutionManifest.Execution(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 1,
                        Instant.parse("2026-07-24T06:00:00Z"),
                        Instant.parse("2026-07-24T07:00:00Z")
                ),
                null,
                null,
                List.of(),
                null,
                new RuntimeFileStorage(
                        "http://minio:9000", "us-east-1", "datascalpel",
                        true, "access", "secret"
                ),
                List.of(input)
        );

        TaskExecutionManifest restored = objectMapper.readValue(
                objectMapper.writeValueAsBytes(manifest),
                TaskExecutionManifest.class
        );

        assertEquals(TaskExecutionManifest.CURRENT_MANIFEST_VERSION, restored.manifestVersion());
        RuntimeFileInput restoredInput = restored.runtimeFileInputs().getFirst();
        assertEquals(tableId, restoredInput.fileDatasetTableId());
        assertEquals(
                List.of(firstSourceId, secondSourceId),
                restoredInput.sources().stream().map(RuntimeFileSource::tableSourceId).toList()
        );
        assertEquals(
                List.of("datasets/first.jsonl", "datasets/second.jsonl.gz"),
                restoredInput.sources().stream().map(RuntimeFileSource::objectKey).toList()
        );
    }

    @Test
    void rejectsUnknownManifestFields() {
        String json = """
                {
                  "manifestVersion":2,
                  "execution":null,
                  "task":null,
                  "metadataSnapshot":null,
                  "runtimeDataSources":[],
                  "unexpected":"unsafe"
                }
                """;

        assertThrows(Exception.class, () -> objectMapper.readValue(json, TaskExecutionManifest.class));
    }

    @Test
    void roundTripsStrictV3FailureWithSharedDiagnosticId() throws Exception {
        Instant startedAt = Instant.parse("2026-07-17T08:00:00Z");
        Instant endedAt = startedAt.plusMillis(12);
        UUID diagnosticId = UUID.randomUUID();
        String nodeId = "65b9615d-b72a-42c1-8e4e-f28a660da082";
        TaskExecutionError error = new TaskExecutionError(
                "JDBC_PERMISSION_DENIED", "数据源用户无权读取表 dev_source.sys_user",
                ExecutionErrorCategory.PERMISSION, false, nodeId, "JDBC_INPUT", "用户输入",
                ExecutionFailurePhase.READ, "42501", diagnosticId);
        TaskExecutionResult result = new TaskExecutionResult(
                TaskExecutionResult.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(), UUID.randomUUID(), 1, TaskExecutionState.FAILED,
                startedAt, endedAt, 12L, null,
                List.of(new NodeExecutionResult(
                        nodeId, "JDBC_INPUT", "用户输入", NodeExecutionState.FAILED,
                        ExecutionFailurePhase.READ, startedAt, endedAt, 12L, null,
                        error.message(), error)), error);

        String json = objectMapper.writeValueAsString(result);
        TaskExecutionResult restored = objectMapper.readValue(json, TaskExecutionResult.class);

        assertEquals(TaskExecutionResult.CURRENT_SCHEMA_VERSION, restored.schemaVersion());
        assertEquals(diagnosticId, restored.error().diagnosticId());
        assertEquals(diagnosticId, restored.nodeResults().getFirst().error().diagnosticId());
        assertEquals(NodeExecutionState.FAILED, restored.nodeResults().getFirst().state());
    }

    @Test
    void roundTripsSnapshotSyncMetricsInResultV3() throws Exception {
        Instant startedAt = Instant.parse("2026-08-06T04:00:00Z");
        SnapshotSyncMetrics metrics = new SnapshotSyncMetrics(10, 9, 2, 1, 0, 7, 1);
        NodeExecutionResult node = new NodeExecutionResult(
                "snapshot-node", "JDBC_SNAPSHOT_SYNC_OUTPUT", "水库快照同步",
                NodeExecutionState.SUCCESS, ExecutionFailurePhase.WRITE,
                startedAt, startedAt.plusMillis(20), 20L, 3L, metrics,
                "快照同步完成", null);
        TaskExecutionResult result = new TaskExecutionResult(
                TaskExecutionResult.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(), UUID.randomUUID(), 1, TaskExecutionState.SUCCESS,
                startedAt, startedAt.plusMillis(20), 20L, 3L, List.of(node), null);

        String json = objectMapper.writeValueAsString(result);
        TaskExecutionResult restored = objectMapper.readValue(json, TaskExecutionResult.class);

        SnapshotSyncMetrics restoredMetrics = assertInstanceOf(
                SnapshotSyncMetrics.class, restored.nodeResults().getFirst().metrics());
        assertEquals(2, restoredMetrics.insertedRows());
        assertEquals(1, restoredMetrics.updatedRows());
        assertTrue(json.contains("\"kind\":\"SNAPSHOT_SYNC\""));
    }

    @Test
    void roundTripsStrictV6SparkJarObservabilityResultAndRunnerEvent() throws Exception {
        Instant capturedAt = Instant.parse("2026-08-14T06:00:00Z");
        UserJobObservabilitySnapshot snapshot = new UserJobObservabilitySnapshot(
                capturedAt,
                new UserJobStatus("WRITE_OUTPUT", "正在写入结果", capturedAt),
                List.of(
                        UserJobMetricSnapshot.counter("datascalpel.model.write.successes", 1),
                        UserJobMetricSnapshot.counter("orders.rows", 12),
                        UserJobMetricSnapshot.timer("orders.write", 1, 8, 8, 8)
                ));
        UUID executionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        TaskExecutionResult result = new TaskExecutionResult(
                TaskExecutionResult.CURRENT_SCHEMA_VERSION,
                executionId, runId, 1, TaskExecutionState.SUCCESS,
                capturedAt, capturedAt.plusMillis(8), 8L, 12L, List.of(),
                ExecutionTaskType.SPARK_JAR, null, snapshot, null);

        TaskExecutionResult restored = objectMapper.readValue(
                objectMapper.writeValueAsString(result), TaskExecutionResult.class);

        assertEquals(6, restored.schemaVersion());
        assertEquals("WRITE_OUTPUT", restored.userJobObservability().status().phase());
        assertEquals(List.of(
                        "datascalpel.model.write.successes", "orders.rows", "orders.write"),
                restored.userJobObservability().metrics().stream()
                        .map(UserJobMetricSnapshot::name).toList());

        UUID engineId = UUID.randomUUID();
        RunnerUserObservabilityEvent event = new RunnerUserObservabilityEvent(
                ExecutionMessageEnvelope.CURRENT_VERSION, UUID.randomUUID(),
                ExecutionMessageType.RUNNER_USER_OBSERVABILITY, capturedAt,
                engineId, executionId, runId, 1, snapshot);
        RunnerExecutionEvent restoredEvent = objectMapper.readValue(
                objectMapper.writeValueAsString(event), RunnerExecutionEvent.class);

        RunnerUserObservabilityEvent observabilityEvent = assertInstanceOf(
                RunnerUserObservabilityEvent.class, restoredEvent);
        assertEquals(snapshot, observabilityEvent.observability());
    }

    @Test
    void rejectsInvalidObservabilityOwnershipAndReservedMetrics() {
        Instant now = Instant.parse("2026-08-14T06:00:00Z");
        UserJobObservabilitySnapshot snapshot = new UserJobObservabilitySnapshot(
                now, new UserJobStatus("RUNNING", "正在运行", now), List.of());

        assertThrows(IllegalArgumentException.class, () -> new TaskExecutionResult(
                TaskExecutionResult.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(), UUID.randomUUID(), 1, TaskExecutionState.SUCCESS,
                now, now, 0L, null, List.of(),
                ExecutionTaskType.SPARK_CANVAS, null, snapshot, null));
        assertThrows(IllegalArgumentException.class, () -> new UserJobObservabilitySnapshot(
                now, null, List.of(UserJobMetricSnapshot.counter("datascalpel.unknown", 1))));
        assertThrows(IllegalArgumentException.class, () -> new UserJobObservabilitySnapshot(
                now, null, java.util.stream.IntStream.rangeClosed(0, 100)
                        .mapToObj(index -> UserJobMetricSnapshot.counter(
                                "metric.%03d".formatted(index), 1))
                        .toList()));
    }
}
