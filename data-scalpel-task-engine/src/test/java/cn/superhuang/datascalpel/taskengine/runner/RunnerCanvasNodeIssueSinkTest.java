package cn.superhuang.datascalpel.taskengine.runner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunnerCanvasNodeIssueSinkTest {

    @Test
    void ignoresWarningsAlreadyReportedByMandatoryPreflight() {
        RunnerCanvasNodeIssueSink issues = new RunnerCanvasNodeIssueSink("node-1");

        assertDoesNotThrow(() -> issues.warning("RISK", "存在风险", "configuration.value"));
        assertFalse(issues.hasErrors());
    }

    @Test
    void turnsSharedOperatorErrorsIntoStableRuntimeFailures() {
        RunnerCanvasNodeIssueSink issues = new RunnerCanvasNodeIssueSink("node-1");

        RunnerExecutionException exception = assertThrows(
                RunnerExecutionException.class,
                () -> issues.error("INVALID", "配置无效", "configuration.value")
        );

        assertEquals("INVALID", exception.code());
        assertEquals("node-1", exception.nodeId());
    }
}
