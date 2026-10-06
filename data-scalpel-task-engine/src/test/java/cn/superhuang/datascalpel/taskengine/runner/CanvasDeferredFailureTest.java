package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.datascalpel.taskengine.contract.NodeExecutionResult;
import cn.superhuang.datascalpel.taskengine.contract.NodeExecutionState;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionError;
import cn.superhuang.datascalpel.taskengine.contract.OutputWritesMetrics;
import cn.superhuang.datascalpel.taskengine.contract.OutputWriteExecutionResult;
import cn.superhuang.datascalpel.taskengine.contract.OutputWriteExecutionState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CanvasDeferredFailureTest {
    @Test
    void preservesCommittedWritesWhenALaterWriteDiscoversADeferredSourceFailure() {
        Instant start = Instant.now();
        String inputId = UUID.randomUUID().toString();
        var error = new TaskExecutionError("FILE_DATASET_PARSE_FAILED", "文件数据集内容解析失败",
                ExecutionErrorCategory.SCHEMA, false, inputId, "FILE_DATASET_INPUT", "CSV输入",
                ExecutionFailurePhase.READ, null, UUID.randomUUID());
        var input = new NodeExecutionResult(inputId, "FILE_DATASET_INPUT", "CSV输入",
                NodeExecutionState.SUCCESS, ExecutionFailurePhase.READ, start, start, 0L,
                null, null, "已准备", null);
        var writes = new OutputWritesMetrics(List.of(
                new OutputWriteExecutionResult(UUID.randomUUID().toString(), "valid", "first",
                        OutputWriteExecutionState.SUCCESS, 10000L, null),
                new OutputWriteExecutionResult(UUID.randomUUID().toString(), "bad_file", "second",
                        OutputWriteExecutionState.FAILED, null, error.code())));
        var output = new NodeExecutionResult(UUID.randomUUID().toString(), "MODEL_OUTPUT", "多目标写入",
                NodeExecutionState.FAILED, ExecutionFailurePhase.WRITE, start, start.plusSeconds(1),
                1000L, 10000L, writes, error.message(), error);
        var reported = CanvasTaskExecutor.preservePartialWriteFailure(List.of(input, output), error);
        var results = CanvasTaskExecutor.attributeDeferredFailure(List.of(input, output), reported);
        assertEquals(2, results.size());
        assertEquals(output.nodeId(), reported.nodeId());
        assertEquals(ExecutionFailurePhase.WRITE, reported.phase());
        assertEquals(error.diagnosticId(), reported.diagnosticId());
        assertSame(reported, results.getLast().error());
        assertSame(writes, results.getLast().metrics());
        assertEquals(10000L, results.getLast().rowsWritten());
        assertEquals(1, results.stream().filter(r -> r.state() == NodeExecutionState.FAILED).count());
    }

    @Test
    void deferredReadFailureReplacesPreparedSourceAndKeepsOneConsistentFailure() {
        String inputId = UUID.randomUUID().toString();
        Instant start = Instant.now();
        var error = new TaskExecutionError("FILE_DATASET_PARSE_FAILED", "文件数据集内容解析失败",
                ExecutionErrorCategory.SCHEMA, false, inputId, "FILE_DATASET_INPUT", "CSV输入",
                ExecutionFailurePhase.READ, null, UUID.randomUUID());
        var input = new NodeExecutionResult(inputId, "FILE_DATASET_INPUT", "CSV输入",
                NodeExecutionState.SUCCESS, ExecutionFailurePhase.READ, start, start.plusMillis(1),
                1L, null, null, "已准备", null);
        var output = new NodeExecutionResult(UUID.randomUUID().toString(), "MODEL_OUTPUT", "入库",
                NodeExecutionState.FAILED, ExecutionFailurePhase.WRITE, start.plusMillis(2), start.plusMillis(5),
                3L, 0L, null, error.message(), error);
        var results = CanvasTaskExecutor.attributeDeferredFailure(List.of(input, output), error);
        assertEquals(1, results.size());
        var failure = results.getFirst();
        assertEquals(NodeExecutionState.FAILED, failure.state());
        assertEquals(error.nodeId(), failure.nodeId());
        assertEquals(error.nodeType(), failure.nodeType());
        assertEquals(error.nodeName(), failure.nodeName());
        assertEquals(error.phase(), failure.phase());
        assertSame(error, failure.error());
        assertEquals(5L, failure.durationMs());
    }
}
