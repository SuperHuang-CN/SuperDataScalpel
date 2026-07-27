package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanvasTaskExecutorTest {

    @Test
    void sumsAllAvailableOutputRows() {
        Long total = CanvasTaskExecutor.addAffectedRows(0L, 2L);
        total = CanvasTaskExecutor.addAffectedRows(total, 3L);

        assertEquals(5L, total);
    }

    @Test
    void keepsTaskTotalUnknownAfterAnyOutputMetricIsUnavailable() {
        Long total = CanvasTaskExecutor.addAffectedRows(0L, 2L);
        total = CanvasTaskExecutor.addAffectedRows(total, null);
        total = CanvasTaskExecutor.addAffectedRows(total, 3L);

        assertNull(total);
    }

    @Test
    void degradesOverflowAndInvalidMetricsToUnknown() {
        assertNull(CanvasTaskExecutor.addAffectedRows(Long.MAX_VALUE, 1L));
        assertNull(CanvasTaskExecutor.addAffectedRows(0L, -1L));
    }

    @Test
    void rejectsManifestVersionOneBeforeStartingSpark() {
        TaskExecutionManifest manifest = new TaskExecutionManifest(1, null, null, null, List.of());

        RunnerExecutionException exception = assertThrows(
                RunnerExecutionException.class,
                () -> new CanvasTaskExecutor().execute(manifest)
        );

        assertEquals("INVALID_MANIFEST", exception.code());
    }
}
