package cn.superhuang.data.scalpel.business.task.execution.service;

import cn.superhuang.data.scalpel.contract.execution.CancelExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.ExecutionArtifactLocation;
import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.data.scalpel.contract.execution.ExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.ExecutionKafkaHeaders;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.execution.SafeExecutionError;
import cn.superhuang.data.scalpel.contract.execution.SubmitExecutionCommand;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionMessageJsonCodecTest {

    private final ExecutionMessageJsonCodec codec = new ExecutionMessageJsonCodec(new ObjectMapper());
    private final UUID engineId = UUID.fromString("ab0b9b23-6602-4e18-9786-9425f1bc5dc6");
    private final UUID executionId = UUID.fromString("875d8295-f8df-4d10-abf8-d3fab01c8aea");
    private final UUID runId = UUID.fromString("ae734bbc-d5d7-4ae8-9144-253719270b11");

    @Test
    void roundTripsCommandDiscriminatedUnionAndDispatcherEvent() {
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        SubmitExecutionCommand submit = new SubmitExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION, now,
                engineId, executionId, runId, 1, UUID.randomUUID(), ExecutionTaskType.SPARK_CANVAS, 3,
                now.plusSeconds(3600), new ExecutionArtifactLocation(
                "task-runs/" + runId + "/attempts/1/manifest.json", "a".repeat(64),
                "task-runs/" + runId + "/attempts/1/result.json",
                "task-runs/" + runId + "/attempts/1/console.log"
        ));

        ExecutionCommand command = codec.readCommand(codec.write(submit));
        assertInstanceOf(SubmitExecutionCommand.class, command);
        assertEquals(executionId, command.executionId());
        assertEquals("SUBMIT_EXECUTION", ExecutionKafkaHeaders.from(command).get(ExecutionKafkaHeaders.MESSAGE_TYPE));

        DispatcherExecutionEvent failed = new DispatcherExecutionEvent(
                1, UUID.randomUUID(), ExecutionMessageType.EXECUTION_FAILED, now.plusSeconds(10),
                engineId, executionId, runId, 1, 4, ExecutionBackendType.LOCAL_DOCKER,
                "container-123", null, now.plusSeconds(1), now.plusSeconds(10), null,
                new SafeExecutionError(
                        "JDBC_PERMISSION_DENIED", "数据源用户无权读取表 dev_source.sys_user",
                        ExecutionErrorCategory.PERMISSION, false,
                        "65b9615d-b72a-42c1-8e4e-f28a660da082", "JDBC_INPUT", "用户输入",
                        ExecutionFailurePhase.READ, "42501", UUID.randomUUID())
        );
        DispatcherExecutionEvent decoded = codec.readDispatcherEvent(codec.write(failed));
        assertEquals(4, decoded.sequence());
        assertEquals("JDBC_PERMISSION_DENIED", decoded.error().code());
        assertEquals(ExecutionErrorCategory.PERMISSION, decoded.error().category());
        assertEquals("42501", decoded.error().sqlState());
    }

    @Test
    void rejectsUnknownFieldsVersionsInvalidKeysAndSha() {
        String cancel = codec.write(new CancelExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.CANCEL_EXECUTION, Instant.now(),
                engineId, executionId, runId, 1, "用户请求停止"
        ));
        assertThrows(RuntimeException.class, () -> codec.readCommand(cancel.substring(0, cancel.length() - 1) + ",\"futureField\":true}"));
        assertThrows(IllegalArgumentException.class, () -> new CancelExecutionCommand(
                2, UUID.randomUUID(), ExecutionMessageType.CANCEL_EXECUTION, Instant.now(),
                engineId, executionId, runId, 1, "取消"
        ));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionArtifactLocation(
                "../manifest.json", "A".repeat(64), "bad", "bad"
        ));
    }
}
