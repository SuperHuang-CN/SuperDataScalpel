package cn.superhuang.data.scalpel.business.filedataset.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileDatasetParseJobTest {

    private static final Instant NOW = Instant.parse("2026-07-19T01:00:00Z");

    @Test
    void queuesClaimsHeartbeatsAndSucceeds() {
        FileDatasetParseJob job = newJob(3);

        assertEquals(FileDatasetParseJobStatus.QUEUED, job.getStatus());
        assertEquals(0, job.getAttemptCount());
        assertEquals(NOW, job.getQueuedAt());
        assertEquals(NOW, job.getAvailableAt());
        assertEquals(FileDatasetTableSourceLoadMode.APPEND, job.getLoadMode());
        assertEquals("道路数据", job.getDatasetNameSnapshot());
        assertEquals("道路", job.getTableNameSnapshot());
        assertEquals("roads.csv", job.getFileNameSnapshot());

        job.claim("admin-1:worker-1", NOW, NOW.plusSeconds(60));
        assertEquals(FileDatasetParseJobStatus.RUNNING, job.getStatus());
        assertEquals(1, job.getAttemptCount());
        assertEquals("admin-1:worker-1", job.getLeaseOwner());

        job.heartbeat("admin-1:worker-1", NOW.plusSeconds(20), NOW.plusSeconds(80));
        assertEquals(NOW.plusSeconds(20), job.getLastHeartbeatAt());
        assertEquals(NOW.plusSeconds(80), job.getLeaseExpiresAt());

        job.succeed("admin-1:worker-1", NOW.plusSeconds(30));
        assertEquals(FileDatasetParseJobStatus.SUCCEEDED, job.getStatus());
        assertEquals(NOW.plusSeconds(30), job.getCompletedAt());
        assertNull(job.getLeaseOwner());
        assertNull(job.getLeaseExpiresAt());
    }

    @Test
    void retriesWithDelayAndFailsAfterTheLastAttempt() {
        FileDatasetParseJob job = newJob(2);
        job.claim("worker-1", NOW, NOW.plusSeconds(60));

        Instant retryAt = NOW.plusSeconds(30);
        job.retry("worker-1", "对象存储暂时不可用", NOW.plusSeconds(10), retryAt);
        assertEquals(FileDatasetParseJobStatus.QUEUED, job.getStatus());
        assertEquals(retryAt, job.getAvailableAt());
        assertEquals("对象存储暂时不可用", job.getErrorMessage());
        assertTrue(job.canRetry());
        assertThrows(
                IllegalStateException.class,
                () -> job.claim("worker-2", retryAt.minusMillis(1), retryAt.plusSeconds(60))
        );

        job.claim("worker-2", retryAt, retryAt.plusSeconds(60));
        assertEquals(2, job.getAttemptCount());
        assertFalse(job.canRetry());
        assertThrows(
                IllegalStateException.class,
                () -> job.retry("worker-2", "仍然失败", retryAt.plusSeconds(10), retryAt.plusSeconds(60))
        );

        job.fail("worker-2", "内容无法读取", retryAt.plusSeconds(10));
        assertEquals(FileDatasetParseJobStatus.FAILED, job.getStatus());
        assertEquals("内容无法读取", job.getErrorMessage());
    }

    @Test
    void rejectsUpdatesFromAWorkerThatDoesNotOwnTheLeaseOrOwnsAnExpiredLease() {
        FileDatasetParseJob job = newJob(3);
        job.claim("worker-1", NOW, NOW.plusSeconds(60));

        assertThrows(
                IllegalStateException.class,
                () -> job.heartbeat("worker-2", NOW.plusSeconds(10), NOW.plusSeconds(70))
        );
        assertThrows(
                IllegalStateException.class,
                () -> job.succeed("worker-1", NOW.plusSeconds(60))
        );
        assertEquals(FileDatasetParseJobStatus.RUNNING, job.getStatus());
    }

    @Test
    void cancelsOnlyQueuedJobsAndTruncatesTheReason() {
        FileDatasetParseJob job = newJob(3);
        String reason = "x".repeat(FileDatasetParseJob.MAX_ERROR_MESSAGE_LENGTH + 20);

        job.cancel(reason, NOW.plusSeconds(1));

        assertEquals(FileDatasetParseJobStatus.CANCELLED, job.getStatus());
        assertEquals(FileDatasetParseJob.MAX_ERROR_MESSAGE_LENGTH, job.getErrorMessage().length());
        assertThrows(IllegalStateException.class, () -> job.cancel("再次取消", NOW.plusSeconds(2)));
    }

    @Test
    void recoversExpiredLeaseAndStopsAfterAttemptsAreExhausted() {
        FileDatasetParseJob job = newJob(2);
        job.claim("worker-1", NOW, NOW.plusSeconds(60));

        assertThrows(
                IllegalStateException.class,
                () -> job.recoverExpiredLease(NOW.plusSeconds(59), NOW.plusSeconds(90), "租约恢复")
        );

        Instant retryAt = NOW.plusSeconds(90);
        job.recoverExpiredLease(NOW.plusSeconds(60), retryAt, "Worker 心跳超时");
        assertEquals(FileDatasetParseJobStatus.QUEUED, job.getStatus());

        job.claim("worker-2", retryAt, retryAt.plusSeconds(60));
        job.recoverExpiredLease(retryAt.plusSeconds(60), retryAt.plusSeconds(90), "Worker 再次超时");
        assertEquals(FileDatasetParseJobStatus.FAILED, job.getStatus());
        assertEquals(retryAt.plusSeconds(60), job.getCompletedAt());
    }

    @Test
    void validatesLoadContextAndSnapshot() {
        UUID datasetId = UUID.randomUUID();
        UUID sourceFileId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();

        assertThrows(
                IllegalArgumentException.class,
                () -> FileDatasetParseJob.queueTableValidation(
                        datasetId, sourceFileId, tableId,
                        FileDatasetTableSourceLoadMode.REPLACE_SOURCE, null,
                        "roads.csv", "__file__", "道路数据", "道路", "roads.csv", 3, NOW
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> FileDatasetParseJob.queueTableValidation(
                        datasetId, sourceFileId, tableId,
                        FileDatasetTableSourceLoadMode.APPEND, UUID.randomUUID(),
                        "roads.csv", "__file__", "道路数据", "道路", "roads.csv", 3, NOW
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> FileDatasetParseJob.queueTableValidation(
                        datasetId, sourceFileId, tableId,
                        FileDatasetTableSourceLoadMode.APPEND, null,
                        "roads.csv", "__file__", "道路数据", "道路", "roads.csv", 11, NOW
                )
        );
    }

    @Test
    void filePreparationMayCarryNoTableLoadContext() {
        FileDatasetParseJob job = FileDatasetParseJob.queueFilePreparation(
                UUID.randomUUID(), UUID.randomUUID(), null,
                null, null, null, null,
                "地理数据库", null, "layers.gdb.zip", 3, NOW
        );

        assertEquals(FileDatasetParseJobType.FILE_PREPARATION, job.getType());
        assertNull(job.getFileDatasetTableId());
        assertNull(job.getLoadMode());
    }

    private static FileDatasetParseJob newJob(int maxAttempts) {
        return FileDatasetParseJob.queueTableValidation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                FileDatasetTableSourceLoadMode.APPEND,
                null,
                "roads.csv",
                "__file__",
                "道路数据",
                "道路",
                "roads.csv",
                maxAttempts,
                NOW
        );
    }
}
