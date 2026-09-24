package cn.superhuang.data.scalpel.business.filedataset.service.queue;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDataset;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFile;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFileStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJob;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobType;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobStatus;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFieldRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFileRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetParseJobQueueRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetParseJobRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableSourceRepository;
import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetTableNamePolicy;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetSchemaValidator;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParser;
import cn.superhuang.data.scalpel.business.filedataset.service.prepare.FileDatasetPreparationService.DiscoveredTable;
import cn.superhuang.data.scalpel.business.filedataset.service.prepare.FileDatasetPreparationService.FileDatasetPreparationResult;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.clearInvocations;

class FileDatasetParseJobCoordinatorTest {

    @Test
    void rechecksRunningSourceAfterCandidateLockBeforeClaiming() {
        Fixture fixture = new Fixture(FileDatasetFormat.GDB);
        when(fixture.jobs.existsBySourceFileIdAndStatusIn(fixture.fileId,
                List.of(FileDatasetParseJobStatus.RUNNING))).thenReturn(true);
        assertEquals(Optional.empty(), fixture.coordinator.claimNext("worker"));
        assertEquals(0, fixture.job.getAttemptCount());
        verify(fixture.files, never()).findLockedById(any());
    }

    @Test
    void staleTableCompletionStillLocksDatasetBeforeJob() {
        Fixture fixture = new Fixture(FileDatasetFormat.GDB);
        var preparation = fixture.coordinator.claimNext("worker").orElseThrow();
        var validation = new FileDatasetParseJobCoordinator.ClaimedJob(preparation.jobId(), "worker",
                FileDatasetParseJobType.TABLE_SOURCE_VALIDATE, fixture.datasetId, fixture.fileId,
                UUID.randomUUID(), null, null);
        fixture.job.succeed("worker", Instant.now());
        var result = mock(FileDatasetParser.ParseResult.class);
        when(result.fields()).thenReturn(List.of(mock(FileDatasetParser.Field.class)));
        when(result.rows()).thenReturn(List.of());
        when(result.sourceMetadata()).thenReturn(java.util.Map.of());
        clearInvocations(fixture.datasets, fixture.jobs);
        assertFalse(fixture.coordinator.completeTableSuccess(validation, result));
        var order = inOrder(fixture.datasets, fixture.jobs);
        order.verify(fixture.datasets).findLockedById(fixture.datasetId);
        order.verify(fixture.jobs).findLockedById(fixture.job.getId());
    }

    @Test
    void failureCompletionLocksDatasetBeforeJobAndDoesNotTouchDeletedDataset() {
        Fixture fixture = new Fixture(FileDatasetFormat.GDB);
        var claim = fixture.coordinator.claimNext("worker").orElseThrow();
        fixture.job.succeed("worker", Instant.now());
        clearInvocations(fixture.datasets, fixture.jobs);
        var failure = new FileDatasetParseFailureClassifier.Failure(false, "invalid content");
        assertEquals(FileDatasetParseJobCoordinator.FailureOutcome.STALE,
                fixture.coordinator.completeFailure(claim, failure));
        var order = inOrder(fixture.datasets, fixture.jobs);
        order.verify(fixture.datasets).findLockedById(fixture.datasetId);
        order.verify(fixture.jobs).findLockedById(fixture.job.getId());
        when(fixture.datasets.findLockedById(fixture.datasetId)).thenReturn(Optional.empty());
        clearInvocations(fixture.jobs);
        assertEquals(FileDatasetParseJobCoordinator.FailureOutcome.STALE,
                fixture.coordinator.completeFailure(claim, failure));
        verify(fixture.jobs, never()).findLockedById(any());
    }

    @ParameterizedTest
    @EnumSource(value = FileDatasetFormat.class, names = {"GDB", "SHP"})
    void eachClaimUsesAnIndependentSiblingPrefixIncludingAttemptTen(FileDatasetFormat format) {
        Fixture fixture = new Fixture(format);
        List<String> prefixes = new ArrayList<>();
        String suffix = format == FileDatasetFormat.GDB ? ".gdb" : "";
        String root = "file-datasets/materialized/" + fixture.fileId + "/";
        prefixes.add(root + fixture.job.getId() + suffix); // A pre-upgrade worker can still be alive.
        for (int attempt = 1; attempt <= 10; attempt++) {
            String owner = "worker-" + attempt;
            var claim = fixture.coordinator.claimNext(owner).orElseThrow();
            String prefix = claim.preparationInput().materializedPrefix();
            assertEquals(root + fixture.job.getId() + "-attempt-" + attempt + suffix, prefix);
            for (String earlier : prefixes) {
                assertNotEquals(earlier, prefix);
                // S3 deletePrefix normalizes the target with a trailing slash.
                assertFalse((prefix + "/data").startsWith(earlier + "/"));
                assertFalse((earlier + "/data").startsWith(prefix + "/"));
            }
            prefixes.add(prefix);
            if (attempt < 10) {
                Instant now = Instant.now();
                fixture.job.retry(owner, "retry", now, now);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = FileDatasetFormat.class, names = {"GDB", "SHP"})
    void expiredAttemptCannotPublishOrFailItsSuccessor(FileDatasetFormat format) {
        Fixture fixture = new Fixture(format);
        var old = fixture.coordinator.claimNext("old-worker").orElseThrow();
        String oldPrefix = old.preparationInput().materializedPrefix();
        // Advance the persisted lease without sleeping or touching a real database.
        ReflectionTestUtils.setField(fixture.job, "leaseExpiresAt", Instant.now().minusSeconds(1));
        Instant now = Instant.now();
        fixture.job.recoverExpiredLease(now, now, "expired");
        var successor = fixture.coordinator.claimNext("new-worker").orElseThrow();
        String successorPrefix = successor.preparationInput().materializedPrefix();
        assertEquals(oldPrefix, old.preparationInput().materializedPrefix());
        assertNotEquals(oldPrefix, successorPrefix);
        var staleResult = new FileDatasetPreparationResult(oldPrefix, 10, 1,
                List.of(new DiscoveredTable("layer", "layer", 0)));
        assertFalse(fixture.coordinator.completePreparationSuccess(old, staleResult));
        assertEquals(FileDatasetParseJobCoordinator.FailureOutcome.STALE,
                fixture.coordinator.completeFailure(old,
                        new FileDatasetParseFailureClassifier.Failure(false, "old failure")));
        assertEquals("new-worker", fixture.job.getLeaseOwner());
        assertEquals(2, fixture.job.getAttemptCount());
        verify(fixture.file, never()).completePreparation(any(), any(), anyLong(), anyInt());
        verify(fixture.files, never()).delete(any(FileDatasetFile.class));
        // Once the successor job is terminal, late success/failure must remain rejected as well.
        fixture.job.succeed("new-worker", Instant.now());
        assertFalse(fixture.coordinator.completePreparationSuccess(old, staleResult));
        assertEquals(FileDatasetParseJobCoordinator.FailureOutcome.STALE,
                fixture.coordinator.completeFailure(old,
                        new FileDatasetParseFailureClassifier.Failure(true, "late failure")));
    }

    private static final class Fixture {
        final UUID datasetId = UUID.randomUUID();
        final UUID fileId = UUID.randomUUID();
        final FileDatasetFile file = mock(FileDatasetFile.class);
        final FileDatasetFileRepository files = mock(FileDatasetFileRepository.class);
        final FileDatasetRepository datasets = mock(FileDatasetRepository.class);
        final FileDatasetParseJobRepository jobs = mock(FileDatasetParseJobRepository.class);
        final FileDatasetParseJob job;
        final FileDatasetParseJobCoordinator coordinator;

        Fixture(FileDatasetFormat format) {
            job = FileDatasetParseJob.queueFilePreparation(datasetId, fileId, null, null, null,
                    null, null, "dataset", null, "archive.zip", 10, Instant.now().minusSeconds(1));
            ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
            var queue = mock(FileDatasetParseJobQueueRepository.class);
            var dataset = mock(FileDataset.class);
            when(queue.lockNextAvailable(any())).thenReturn(Optional.of(job));
            when(jobs.findLockedById(job.getId())).thenReturn(Optional.of(job));
            when(datasets.findById(datasetId)).thenReturn(Optional.of(dataset));
            when(datasets.findLockedById(datasetId)).thenReturn(Optional.of(dataset));
            when(dataset.getParsingOptions()).thenReturn("{}");
            when(files.findLockedById(fileId)).thenReturn(Optional.of(file));
            when(file.getId()).thenReturn(fileId);
            when(file.getFileDatasetId()).thenReturn(datasetId);
            when(file.getCurrentPreparationJobId()).thenReturn(job.getId());
            when(file.getStatus()).thenReturn(FileDatasetFileStatus.PREPARING);
            when(file.getFormat()).thenReturn(format);
            when(file.getObjectKey()).thenReturn("raw/archive.zip");
            var transactions = mock(PlatformTransactionManager.class);
            when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            coordinator = new FileDatasetParseJobCoordinator(queue, jobs, datasets, files,
                    mock(FileDatasetTableRepository.class), mock(FileDatasetTableSourceRepository.class),
                    mock(FileDatasetFieldRepository.class), mock(FileDatasetParseRetryPolicy.class),
                    mock(FileDatasetSchemaValidator.class), mock(FileDatasetTableNamePolicy.class),
                    new StaticListableBeanFactory().getBeanProvider(FileObjectStorage.class),
                    new ObjectMapper(), transactions);
        }
    }
}
