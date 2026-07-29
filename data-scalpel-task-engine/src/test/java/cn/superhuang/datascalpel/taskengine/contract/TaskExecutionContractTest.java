package cn.superhuang.datascalpel.taskengine.contract;

import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationRequest;

import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
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
    void roundTripsManifestV6LogicalFileInputWithOrderedSources() throws Exception {
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
    void roundTripsStrictV2FailureWithSharedDiagnosticId() throws Exception {
        Instant startedAt = Instant.parse("2026-07-17T08:00:00Z");
        Instant endedAt = startedAt.plusMillis(12);
        UUID diagnosticId = UUID.randomUUID();
        String nodeId = "65b9615d-b72a-42c1-8e4e-f28a660da082";
        TaskExecutionError error = new TaskExecutionError(
                "JDBC_PERMISSION_DENIED", "数据源用户无权读取表 dev_source.sys_user",
                ExecutionErrorCategory.PERMISSION, false, nodeId, "JDBC_INPUT", "用户输入",
                ExecutionFailurePhase.READ, "42501", diagnosticId);
        TaskExecutionResult result = new TaskExecutionResult(
                2, UUID.randomUUID(), UUID.randomUUID(), 1, TaskExecutionState.FAILED,
                startedAt, endedAt, 12L, null,
                List.of(new NodeExecutionResult(
                        nodeId, "JDBC_INPUT", "用户输入", NodeExecutionState.FAILED,
                        ExecutionFailurePhase.READ, startedAt, endedAt, 12L, null,
                        error.message(), error)), error);

        String json = objectMapper.writeValueAsString(result);
        TaskExecutionResult restored = objectMapper.readValue(json, TaskExecutionResult.class);

        assertEquals(2, restored.schemaVersion());
        assertEquals(diagnosticId, restored.error().diagnosticId());
        assertEquals(diagnosticId, restored.nodeResults().getFirst().error().diagnosticId());
        assertEquals(NodeExecutionState.FAILED, restored.nodeResults().getFirst().state());
    }
}
