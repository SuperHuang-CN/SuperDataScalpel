package cn.superhuang.data.scalpel.dispatcher;

import cn.superhuang.data.scalpel.contract.execution.ExecutionArtifactLocation;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionUserJarArtifact;
import cn.superhuang.data.scalpel.contract.execution.RunnerResultAvailableEvent;
import cn.superhuang.data.scalpel.contract.execution.SubmitExecutionCommand;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherArtifactService;
import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherResultResolution;
import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherResultService;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionState;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherInboxState;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherAdmissionPolicy;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherRegistrationRequest;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherRegistrationService;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherTopics;
import cn.superhuang.data.scalpel.dispatcher.messaging.MessageCoordinates;
import cn.superhuang.data.scalpel.dispatcher.messaging.command.DispatcherCommandService;
import cn.superhuang.data.scalpel.dispatcher.messaging.runner.DispatcherRunnerEventService;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherMessageInboxRepository;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherEventOutboxRepository;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import cn.superhuang.data.scalpel.dispatcher.service.DispatcherExecutionCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class DispatcherRunnerResultIntegrationTest {
    @Autowired DispatcherRegistrationService registrationService;
    @Autowired DispatcherCommandService commandService;
    @Autowired DispatcherExecutionCoordinator coordinator;
    @Autowired DispatcherRunnerEventService runnerEventService;
    @Autowired DispatcherArtifactService artifactService;
    @Autowired DispatcherResultService resultService;
    @Autowired DispatcherTaskExecutionRepository executionRepository;
    @Autowired DispatcherMessageInboxRepository inboxRepository;
    @Autowired DispatcherEventOutboxRepository outboxRepository;

    @Test
    void verifiesResultArtifactBeforePublishingAuthoritativeSuccess() throws Exception {
        Prepared prepared = prepare("verified");
        byte[] result = result(prepared, "SUCCESS", 42L, null);
        artifactService.store(prepared.resultKey(), result, "application/json");
        RunnerResultAvailableEvent event = available(prepared, sha256(result));

        runnerEventService.accept(event, new MessageCoordinates("runner.verified", 0, 1));

        // The result signal is verified immediately, while the terminal Admin event
        // waits until the backend is terminal and its console log has been stored.
        assertThat(executionRepository.findByExecutionId(prepared.executionId()).orElseThrow().getState())
                .isEqualTo(DispatcherExecutionState.SUBMITTED);
        coordinator.observe();
        coordinator.observe();

        var execution = executionRepository.findByExecutionId(prepared.executionId()).orElseThrow();
        assertThat(execution.getState()).isEqualTo(DispatcherExecutionState.SUCCESS);
        assertThat(execution.getEndedAt()).isAfterOrEqualTo(execution.getStartedAt());
        assertThat(execution.isLogArtifactStored()).isTrue();
        assertThat(inboxRepository.findByMessageId(event.messageId()).orElseThrow().getState())
                .isEqualTo(DispatcherInboxState.PROCESSED);
    }

    @Test
    void rejectsDigestMismatchWithoutChangingExecutionState() throws Exception {
        Prepared prepared = prepare("digest");
        byte[] result = result(prepared, "SUCCESS", 1L, null);
        artifactService.store(prepared.resultKey(), result, "application/json");
        RunnerResultAvailableEvent event = available(prepared, "0".repeat(64));

        runnerEventService.accept(event, new MessageCoordinates("runner.digest", 0, 2));

        assertThat(executionRepository.findByExecutionId(prepared.executionId()).orElseThrow().getState())
                .isEqualTo(DispatcherExecutionState.SUBMITTED);
        assertThat(inboxRepository.findByMessageId(event.messageId()).orElseThrow().getState())
                .isEqualTo(DispatcherInboxState.REJECTED);
    }

    @Test
    void lateRunnerResultCannotOverrideFirstTerminalState() throws Exception {
        Prepared prepared = prepare("late-result");
        var execution = executionRepository.findByExecutionId(prepared.executionId()).orElseThrow();
        execution.fail("RUNNER_FAILED", "Runner 已先报告失败");
        executionRepository.saveAndFlush(execution);
        byte[] result = result(prepared, "SUCCESS", 42L, null);
        artifactService.store(prepared.resultKey(), result, "application/json");
        RunnerResultAvailableEvent event = available(prepared, sha256(result));

        runnerEventService.accept(event, new MessageCoordinates("runner.late-result", 0, 3));

        var reloaded = executionRepository.findByExecutionId(prepared.executionId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(DispatcherExecutionState.FAILED);
        assertThat(reloaded.getSafeErrorCode()).isEqualTo("RUNNER_FAILED");
        assertThat(inboxRepository.findByMessageId(event.messageId()).orElseThrow().getState())
                .isEqualTo(DispatcherInboxState.PROCESSED);
    }

    @Test
    void preservesUnknownAffectedRowsInAuthoritativeSuccessEvent() throws Exception {
        Prepared prepared = prepare("unknown-rows");
        byte[] result = result(prepared, "SUCCESS", null, null);
        artifactService.store(prepared.resultKey(), result, "application/json");

        runnerEventService.accept(
                available(prepared, sha256(result)),
                new MessageCoordinates("runner.unknown-rows", 0, 4));
        coordinator.observe();
        coordinator.observe();

        var success = outboxRepository.findAll().stream()
                .filter(event -> event.getExecutionId().equals(prepared.executionId()))
                .filter(event -> event.getMessageType().equals(ExecutionMessageType.EXECUTION_SUCCEEDED.name()))
                .findFirst().orElseThrow();
        assertThat(success.getPayload()).contains("\"affectedRows\":null");
    }

    @Test
    void propagatesStructuredRunnerFailureAndRejectsV1() throws Exception {
        Prepared prepared = prepare("structured-failure");
        Instant now = Instant.now();
        String diagnosticId = UUID.randomUUID().toString();
        String nodeId = "65b9615d-b72a-42c1-8e4e-f28a660da082";
        String error = """
                {"code":"JDBC_PERMISSION_DENIED","message":"数据源用户无权读取表 dev_source.sys_user",
                 "category":"PERMISSION","retryable":false,"nodeId":"%s","nodeType":"JDBC_INPUT",
                 "nodeName":"用户输入","phase":"READ","sqlState":"42501","diagnosticId":"%s"}
                """.formatted(nodeId, diagnosticId).strip();
        byte[] failedResult = ("""
                {
                  "schemaVersion":2,"executionId":"%s","runId":"%s","attempt":1,
                  "state":"FAILED","startedAt":"%s","endedAt":"%s","durationMs":1,
                  "affectedRows":null,
                  "nodeResults":[{"nodeId":"%s","nodeType":"JDBC_INPUT","nodeName":"用户输入",
                    "state":"FAILED","phase":"READ","startedAt":"%s","endedAt":"%s","durationMs":1,
                    "rowsWritten":null,"message":"数据源用户无权读取表 dev_source.sys_user","error":%s}],
                  "error":%s
                }
                """).formatted(prepared.executionId(), prepared.runId(), now.minusMillis(1), now, nodeId,
                now.minusMillis(1), now, error, error).getBytes(StandardCharsets.UTF_8);
        artifactService.store(prepared.resultKey(), failedResult, "application/json");

        runnerEventService.accept(available(prepared, sha256(failedResult)),
                new MessageCoordinates("runner.structured-failure", 0, 5));
        coordinator.observe();
        coordinator.observe();

        var execution = executionRepository.findByExecutionId(prepared.executionId()).orElseThrow();
        assertThat(execution.getState()).isEqualTo(DispatcherExecutionState.FAILED);
        assertThat(execution.getSafeExecutionError()).isNotNull();
        assertThat(execution.getSafeExecutionError().sqlState()).isEqualTo("42501");
        assertThat(execution.getSafeExecutionError().nodeId()).isEqualTo(nodeId);
        assertThat(execution.getSafeExecutionError().diagnosticId().toString()).isEqualTo(diagnosticId);

        byte[] v1 = new String(result(prepared, "SUCCESS", 1L, null), StandardCharsets.UTF_8)
                .replace("\"schemaVersion\":2", "\"schemaVersion\":1")
                .getBytes(StandardCharsets.UTF_8);
        artifactService.store(prepared.resultKey(), v1, "application/json");
        assertThat(resultService.reconcile(prepared.executionId(), null))
                .isEqualTo(DispatcherResultResolution.ARTIFACT_INVALID);
    }

    @Test
    void acceptsModelNodeResultsAndStillRejectsUnknownNodeTypes() throws Exception {
        Prepared prepared = prepare("model-node-types");
        Instant now = Instant.now();
        String inputNodeId = "3c1f4d60-2693-4a2a-9df7-b50433a63b9f";
        String outputNodeId = "6395b9d2-54a8-4492-bad1-bdc61a179458";
        byte[] modelResult = ("""
                {
                  "schemaVersion":2,"executionId":"%s","runId":"%s","attempt":1,
                  "state":"SUCCESS","startedAt":"%s","endedAt":"%s","durationMs":2,
                  "affectedRows":2,
                  "nodeResults":[
                    {"nodeId":"%s","nodeType":"MODEL_INPUT","nodeName":"订单模型输入",
                     "state":"SUCCESS","phase":"READ","startedAt":"%s","endedAt":"%s",
                     "durationMs":1,"rowsWritten":null,"message":"模型输入已准备","error":null},
                    {"nodeId":"%s","nodeType":"MODEL_OUTPUT","nodeName":"订单模型输出",
                     "state":"SUCCESS","phase":"WRITE","startedAt":"%s","endedAt":"%s",
                     "durationMs":1,"rowsWritten":2,"message":"模型输出写入成功","error":null}
                  ],
                  "error":null
                }
                """).formatted(
                prepared.executionId(), prepared.runId(), now.minusMillis(2), now,
                inputNodeId, now.minusMillis(2), now.minusMillis(1),
                outputNodeId, now.minusMillis(1), now
        ).getBytes(StandardCharsets.UTF_8);
        artifactService.store(prepared.resultKey(), modelResult, "application/json");

        assertThat(resultService.verify(prepared.executionId(), sha256(modelResult)))
                .isEqualTo(DispatcherResultResolution.VERIFIED);

        byte[] unknownNodeResult = new String(modelResult, StandardCharsets.UTF_8)
                .replace("\"MODEL_OUTPUT\"", "\"UNKNOWN_OUTPUT\"")
                .getBytes(StandardCharsets.UTF_8);
        artifactService.store(prepared.resultKey(), unknownNodeResult, "application/json");
        assertThat(resultService.verify(prepared.executionId(), sha256(unknownNodeResult)))
                .isEqualTo(DispatcherResultResolution.ARTIFACT_INVALID);
    }

    @Test
    void acceptsV7MultiOutputWritesAsOneNodeResult() throws Exception {
        Prepared prepared = prepare("v7-multi-output");
        Instant now = Instant.now();
        String nodeId = UUID.randomUUID().toString();
        byte[] result = ("""
                {
                  "schemaVersion":7,"executionId":"%s","runId":"%s","attempt":1,
                  "state":"SUCCESS","startedAt":"%s","endedAt":"%s","durationMs":2,
                  "affectedRows":10723,"taskType":"SPARK_CANVAS",
                  "nodeResults":[{
                    "nodeId":"%s","nodeType":"MODEL_OUTPUT","nodeName":"模型输出",
                    "state":"SUCCESS","phase":"WRITE","startedAt":"%s","endedAt":"%s",
                    "durationMs":2,"rowsWritten":10723,
                    "metrics":{"kind":"OUTPUT_WRITES","writes":[
                      {"writeId":"%s","sourceTableName":"source_a","targetDisplayName":"target_a",
                       "state":"SUCCESS","affectedRows":10562,"errorCode":null},
                      {"writeId":"%s","sourceTableName":"source_b","targetDisplayName":"target_b",
                       "state":"SUCCESS","affectedRows":161,"errorCode":null}
                    ]},
                    "message":"模型输出写入成功","error":null
                  }],
                  "error":null
                }
                """).formatted(
                prepared.executionId(), prepared.runId(), now.minusMillis(2), now,
                nodeId, now.minusMillis(2), now,
                UUID.randomUUID(), UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        artifactService.store(prepared.resultKey(), result, "application/json");

        assertThat(resultService.verify(prepared.executionId(), sha256(result)))
                .isEqualTo(DispatcherResultResolution.VERIFIED);
    }

    @Test
    void acceptsV7PartialOutputFailureAndRejectsDuplicateNodeIds() throws Exception {
        Prepared prepared = prepare("v7-partial-output");
        Instant now = Instant.now();
        String nodeId = UUID.randomUUID().toString();
        String diagnosticId = UUID.randomUUID().toString();
        String error = """
                {"code":"JDBC_WRITE_FAILED","message":"第二项目标写入失败",
                 "category":"EXTERNAL_SYSTEM","retryable":false,"nodeId":"%s",
                 "nodeType":"JDBC_OUTPUT","nodeName":"JDBC 输出","phase":"WRITE",
                 "sqlState":null,"diagnosticId":"%s"}
                """.formatted(nodeId, diagnosticId).strip();
        String node = """
                {"nodeId":"%s","nodeType":"JDBC_OUTPUT","nodeName":"JDBC 输出",
                 "state":"FAILED","phase":"WRITE","startedAt":"%s","endedAt":"%s",
                 "durationMs":2,"rowsWritten":42,
                 "metrics":{"kind":"OUTPUT_WRITES","writes":[
                   {"writeId":"%s","sourceTableName":"source_a","targetDisplayName":"target_a",
                    "state":"SUCCESS","affectedRows":42,"errorCode":null},
                   {"writeId":"%s","sourceTableName":"source_b","targetDisplayName":"target_b",
                    "state":"FAILED","affectedRows":null,"errorCode":"JDBC_WRITE_FAILED"},
                   {"writeId":"%s","sourceTableName":"source_c","targetDisplayName":"target_c",
                    "state":"SKIPPED","affectedRows":null,"errorCode":null}
                 ]},"message":"第二项目标写入失败","error":%s}
                """.formatted(
                nodeId, now.minusMillis(2), now,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), error).strip();
        byte[] result = ("""
                {"schemaVersion":7,"executionId":"%s","runId":"%s","attempt":1,
                 "state":"FAILED","startedAt":"%s","endedAt":"%s","durationMs":2,
                 "affectedRows":42,"taskType":"SPARK_CANVAS",
                 "nodeResults":[%s],"error":%s}
                """).formatted(
                prepared.executionId(), prepared.runId(), now.minusMillis(2), now,
                node, error).getBytes(StandardCharsets.UTF_8);
        artifactService.store(prepared.resultKey(), result, "application/json");

        assertThat(resultService.verify(prepared.executionId(), sha256(result)))
                .isEqualTo(DispatcherResultResolution.VERIFIED);

        byte[] unknownRowsResult = new String(result, StandardCharsets.UTF_8)
                .replace("\"affectedRows\":42", "\"affectedRows\":null")
                .replace("\"rowsWritten\":42", "\"rowsWritten\":null")
                .getBytes(StandardCharsets.UTF_8);
        artifactService.store(prepared.resultKey(), unknownRowsResult, "application/json");
        assertThat(resultService.verify(prepared.executionId(), sha256(unknownRowsResult)))
                .isEqualTo(DispatcherResultResolution.VERIFIED);

        byte[] inconsistentTotalResult = new String(result, StandardCharsets.UTF_8)
                .replaceFirst("\\\"affectedRows\\\":42", "\\\"affectedRows\\\":41")
                .getBytes(StandardCharsets.UTF_8);
        artifactService.store(prepared.resultKey(), inconsistentTotalResult, "application/json");
        assertThat(resultService.verify(prepared.executionId(), sha256(inconsistentTotalResult)))
                .isEqualTo(DispatcherResultResolution.ARTIFACT_INVALID);

        byte[] invalidSequenceResult = new String(result, StandardCharsets.UTF_8)
                .replaceFirst("\\\"state\\\":\\\"SUCCESS\\\"", "\\\"state\\\":\\\"SKIPPED\\\"")
                .getBytes(StandardCharsets.UTF_8);
        artifactService.store(prepared.resultKey(), invalidSequenceResult, "application/json");
        assertThat(resultService.verify(prepared.executionId(), sha256(invalidSequenceResult)))
                .isEqualTo(DispatcherResultResolution.ARTIFACT_INVALID);

        byte[] duplicateNodeResult = new String(result, StandardCharsets.UTF_8)
                .replace("\"nodeResults\":[" + node + "]", "\"nodeResults\":[" + node + "," + node + "]")
                .getBytes(StandardCharsets.UTF_8);
        artifactService.store(prepared.resultKey(), duplicateNodeResult, "application/json");
        assertThat(resultService.verify(prepared.executionId(), sha256(duplicateNodeResult)))
                .isEqualTo(DispatcherResultResolution.ARTIFACT_INVALID);
    }

    @Test
    void acceptsV8SparkJarLineageAndPublishesOnlyItsResultDigest() throws Exception {
        Prepared prepared = prepare("v8-spark-jar-lineage", ExecutionTaskType.SPARK_JAR);
        Instant now = Instant.now();
        UUID dataSourceId = UUID.randomUUID();
        byte[] result = ("""
                {
                  "schemaVersion":8,"executionId":"%s","runId":"%s","attempt":1,
                  "state":"SUCCESS","startedAt":"%s","endedAt":"%s","durationMs":2,
                  "affectedRows":2,"taskType":"SPARK_JAR","nodeResults":[],
                  "lineage":{"analysisStatus":"PARTIAL","coverage":"MODEL_ONLY","flows":[{
                    "flowKey":"jar:%s","producerKey":"jar:write:%s","producerType":"SDK_JDBC_WRITE",
                    "coverage":"MODEL_ONLY",
                    "outputAsset":{"localAssetKey":"output","role":"OUTPUT","kind":"JDBC_TABLE",
                      "writeMode":"APPEND","dataSourceId":"%s","physicalTableName":"target_table",
                      "safeDisplayName":"target_table"},
                    "inputAssets":[{"localAssetKey":"input:source","role":"INPUT","kind":"JDBC_TABLE",
                      "dataSourceId":"%s","physicalTableName":"source_table",
                      "safeDisplayName":"source_table"}],
                    "fields":[],"fieldEdges":[],"fieldUsages":[],"warnings":[]
                  }],"warnings":[]},"error":null
                }
                """).formatted(
                prepared.executionId(), prepared.runId(), now.minusMillis(2), now,
                "a".repeat(64), "a".repeat(64), dataSourceId, dataSourceId
        ).getBytes(StandardCharsets.UTF_8);
        String digest = sha256(result);
        artifactService.store(prepared.resultKey(), result, "application/json");

        runnerEventService.accept(available(prepared, digest),
                new MessageCoordinates("runner.v8-spark-jar-lineage", 0, 10));
        coordinator.observe();
        coordinator.observe();

        var execution = executionRepository.findByExecutionId(prepared.executionId()).orElseThrow();
        assertThat(execution.getResultSha256()).isEqualTo(digest);
        var success = outboxRepository.findAll().stream()
                .filter(event -> event.getExecutionId().equals(prepared.executionId()))
                .filter(event -> event.getMessageType().equals(ExecutionMessageType.EXECUTION_SUCCEEDED.name()))
                .findFirst().orElseThrow();
        assertThat(success.getPayload()).contains("\"resultSha256\":\"" + digest + "\"");
        assertThat(success.getPayload()).doesNotContain("source_table");
    }

    @Test
    void degradesInvalidV8SparkJarLineageWithoutRejectingSuccessfulResult() throws Exception {
        Prepared prepared = prepare("v8-invalid-spark-jar-lineage", ExecutionTaskType.SPARK_JAR);
        Instant now = Instant.now();
        UUID dataSourceId = UUID.randomUUID();
        byte[] result = ("""
                {
                  "schemaVersion":8,"executionId":"%s","runId":"%s","attempt":1,
                  "state":"SUCCESS","startedAt":"%s","endedAt":"%s","durationMs":2,
                  "affectedRows":2,"taskType":"SPARK_JAR","nodeResults":[],
                  "lineage":{"analysisStatus":"COMPLETE","coverage":"FIELD_COMPLETE","flows":[{
                    "flowKey":"jar:%s","producerKey":"jar:write:%s","producerType":"SDK_JDBC_WRITE",
                    "coverage":"FIELD_COMPLETE",
                    "outputAsset":{"localAssetKey":"output","role":"OUTPUT","kind":"JDBC_TABLE",
                      "writeMode":"APPEND","dataSourceId":"%s","physicalTableName":"target_table",
                      "safeDisplayName":"target_table"},
                    "inputAssets":[{"localAssetKey":"input:source","role":"INPUT","kind":"JDBC_TABLE",
                      "dataSourceId":"%s","physicalTableName":"source_table",
                      "safeDisplayName":"source_table"}],
                    "fields":[],"fieldEdges":[],
                    "fieldUsages":[{"field":{"localAssetKey":"input:source",
                      "localFieldKey":"jdbc-column:id"},"nodeKey":null,"usageType":"JOIN_KEY"}],
                    "warnings":[]
                  }],"warnings":[]},"error":null
                }
                """).formatted(
                prepared.executionId(), prepared.runId(), now.minusMillis(2), now,
                "a".repeat(64), "a".repeat(64), dataSourceId, dataSourceId
        ).getBytes(StandardCharsets.UTF_8);
        artifactService.store(prepared.resultKey(), result, "application/json");

        assertThat(resultService.verify(prepared.executionId(), sha256(result)))
                .isEqualTo(DispatcherResultResolution.VERIFIED);
    }

    @Test
    void acceptsEveryNodeTypeDeclaredByTheStableCanvasContract() throws Exception {
        Prepared prepared = prepare("all-canvas-node-types");
        Instant now = Instant.now();
        for (CanvasNodeType nodeType : CanvasNodeType.values()) {
            String nodeId = UUID.randomUUID().toString();
            byte[] result = ("""
                    {
                      "schemaVersion":2,"executionId":"%s","runId":"%s","attempt":1,
                      "state":"SUCCESS","startedAt":"%s","endedAt":"%s","durationMs":1,
                      "affectedRows":null,
                      "nodeResults":[{"nodeId":"%s","nodeType":"%s","nodeName":"契约节点",
                        "state":"SUCCESS","phase":"PROCESS","startedAt":"%s","endedAt":"%s",
                        "durationMs":1,"rowsWritten":null,"message":"节点执行成功","error":null}],
                      "error":null
                    }
                    """).formatted(
                    prepared.executionId(), prepared.runId(), now.minusMillis(1), now,
                    nodeId, nodeType.name(), now.minusMillis(1), now
            ).getBytes(StandardCharsets.UTF_8);
            artifactService.store(prepared.resultKey(), result, "application/json");

            assertThat(resultService.verify(prepared.executionId(), sha256(result)))
                    .as(nodeType.name())
                    .isEqualTo(DispatcherResultResolution.VERIFIED);
        }
    }

    private Prepared prepare(String suffix) {
        return prepare(suffix, ExecutionTaskType.SPARK_CANVAS);
    }

    private Prepared prepare(String suffix, ExecutionTaskType taskType) {
        UUID engineId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String commandTopic = "commands." + suffix;
        String runnerTopic = "runner." + suffix;
        registrationService.activate(new DispatcherRegistrationRequest(
                engineId,
                new DispatcherTopics(commandTopic, runnerTopic, "admin." + suffix),
                new DispatcherAdmissionPolicy(20, 2, 2), cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy.defaultsFor(cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType.LOCAL_DOCKER)));
        String prefix = "task-runs/" + runId + "/attempts/1/";
        Instant now = Instant.now();
        ExecutionUserJarArtifact userJar = taskType == ExecutionTaskType.SPARK_JAR
                ? new ExecutionUserJarArtifact(prefix + "user-job.jar", "b".repeat(64), 1)
                : null;
        SubmitExecutionCommand command = new SubmitExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION, now,
                engineId, executionId, runId, 1, UUID.randomUUID(), taskType, 1,
                now.plusSeconds(3600), new ExecutionArtifactLocation(
                prefix + "manifest.json", "a".repeat(64), prefix + "result.json", prefix + "console.log"),
                List.of(), 0, userJar, List.of());
        commandService.accept(command, new MessageCoordinates(commandTopic, 0, 1));
        coordinator.admit();
        assertThat(executionRepository.findByExecutionId(executionId).orElseThrow().getState())
                .isEqualTo(DispatcherExecutionState.SUBMITTED);
        return new Prepared(engineId, executionId, runId, prefix + "result.json");
    }

    private static RunnerResultAvailableEvent available(Prepared prepared, String digest) {
        return new RunnerResultAvailableEvent(
                1, UUID.randomUUID(), ExecutionMessageType.RUNNER_RESULT_AVAILABLE, Instant.now(),
                prepared.engineId(), prepared.executionId(), prepared.runId(), 1, prepared.resultKey(), digest);
    }

    private static byte[] result(Prepared prepared, String state, Long rows, String error) {
        Instant now = Instant.now();
        return ("""
                {
                  "schemaVersion":2,"executionId":"%s","runId":"%s","attempt":1,
                  "state":"%s","startedAt":"%s","endedAt":"%s","durationMs":1,
                  "affectedRows":%s,"nodeResults":[],"error":%s
                }
                """).formatted(prepared.executionId(), prepared.runId(), state, now.minusMillis(1), now,
                rows == null ? "null" : rows,
                error == null ? "null" : error).getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private record Prepared(UUID engineId, UUID executionId, UUID runId, String resultKey) { }
}
