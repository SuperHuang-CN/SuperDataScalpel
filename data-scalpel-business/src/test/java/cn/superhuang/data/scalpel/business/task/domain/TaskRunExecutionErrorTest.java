package cn.superhuang.data.scalpel.business.task.domain;

import cn.superhuang.data.scalpel.business.task.web.response.TaskRunResponse;
import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.data.scalpel.contract.execution.SafeExecutionError;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TaskRunExecutionErrorTest {

    @Test
    void persistsAndExposesStructuredCanvasExecutionError() {
        UUID diagnosticId = UUID.randomUUID();
        TaskRun run = TaskRun.queueDispatchedCanvas(
                UUID.randomUUID(), UUID.randomUUID(), 3, "{}", UUID.randomUUID(), 1,
                Instant.now().plusSeconds(300), UUID.randomUUID(), "datascalpel.commands.local");
        SafeExecutionError error = new SafeExecutionError(
                "JDBC_PERMISSION_DENIED", "数据源用户无权读取表 dev_source.sys_user",
                ExecutionErrorCategory.PERMISSION, false,
                "65b9615d-b72a-42c1-8e4e-f28a660da082", "JDBC_INPUT", "用户输入",
                ExecutionFailurePhase.READ, "42501", diagnosticId);

        run.fail(error);
        TaskRunResponse response = TaskRunResponse.from(run);

        assertEquals(TaskRunStatus.FAILED, response.status());
        assertEquals("JDBC_PERMISSION_DENIED", response.errorDetail());
        assertNotNull(response.executionError());
        assertEquals(ExecutionErrorCategory.PERMISSION, response.executionError().category());
        assertEquals("42501", response.executionError().sqlState());
        assertEquals(diagnosticId, response.executionError().diagnosticId());
        assertFalse(response.executionError().retryable());
    }
}
