package cn.superhuang.data.scalpel.admin.filedataset;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDataset;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetCompression;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFile;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJob;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobType;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTable;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTableSourceLoadMode;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetType;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFieldRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFileRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetParseJobQueueRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetParseJobRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableSourceRepository;
import cn.superhuang.data.scalpel.business.filedataset.service.queue.FileDatasetParseJobCoordinator;
import cn.superhuang.data.scalpel.business.filedataset.service.queue.FileDatasetParseWorker;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetContentParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParser;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageException;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageObjectNotFoundException;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ActiveProfiles("test")
@SpringBootTest
@Import(FileDatasetParseWorkerIntegrationTests.StorageConfiguration.class)
class FileDatasetParseWorkerIntegrationTests {

    private static final String CSV_OPTIONS = """
            {"kind":"CSV","charset":"UTF-8","fieldDelimiter":",","recordDelimiter":"AUTO",
             "quoteCharacter":"\\\"","escapeCharacter":"\\\\","firstRowHeader":true}
            """;

    @Autowired
    private FileDatasetRepository datasetRepository;

    @Autowired
    private FileDatasetFileRepository fileRepository;

    @Autowired
    private FileDatasetTableRepository tableRepository;

    @Autowired
    private FileDatasetTableSourceRepository sourceRepository;

    @Autowired
    private FileDatasetFieldRepository fieldRepository;

    @Autowired
    private FileDatasetParseJobRepository jobRepository;

    @Autowired
    private FileDatasetParseJobQueueRepository queueRepository;

    @Autowired
    private FileDatasetParseJobCoordinator coordinator;

    @Autowired
    private FileDatasetParseWorker worker;

    @Autowired
    private InMemoryQueueStorage storage;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        clearData();
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    @Test
    void parsesAndCommitsJobTableAndSchemaInOneWorkerRun() {
        Fixture fixture = enqueue(
                "success.csv", "id,name\n1,道路\n".getBytes(StandardCharsets.UTF_8),
                3, Instant.now().minusSeconds(2)
        );

        FileDatasetParseWorker.ExecutionOutcome outcome = worker.runOne("test-worker-success");

        assertThat(outcome).isEqualTo(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED);
        FileDatasetParseJob job = jobRepository.findById(fixture.jobId()).orElseThrow();
        FileDatasetTable table = tableRepository.findById(fixture.tableId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(FileDatasetParseJobStatus.SUCCEEDED);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(table.getParseStatus()).isEqualTo(FileDatasetParseStatus.READY);
        assertThat(table.getCurrentLoadJobId()).isNull();
        assertThat(sourceRepository.findByFileDatasetTableIdOrderBySourceOrderAsc(table.getId()))
                .hasSize(1);
        assertThat(fieldRepository.findByFileDatasetTableIdOrderBySortOrderAsc(table.getId()))
                .extracting("name")
                .containsExactly("id", "name");
    }

    @Test
    void retriesStorageFailureButFinalizesMissingContent() {
        Fixture retryable = enqueue(
                "retry.csv", "id,name\n1,道路\n".getBytes(StandardCharsets.UTF_8),
                3, Instant.now().minusSeconds(3)
        );
        storage.failNextOpen();

        assertThat(worker.runOne("test-worker-retry"))
                .isEqualTo(FileDatasetParseWorker.ExecutionOutcome.REQUEUED);
        FileDatasetParseJob retriedJob = jobRepository.findById(retryable.jobId()).orElseThrow();
        FileDatasetTable retriedTable = tableRepository.findById(retryable.tableId()).orElseThrow();
        assertThat(retriedJob.getStatus()).isEqualTo(FileDatasetParseJobStatus.QUEUED);
        assertThat(retriedJob.getAttemptCount()).isEqualTo(1);
        assertThat(retriedJob.getAvailableAt()).isAfter(Instant.now());
        assertThat(retriedTable.getParseStatus()).isEqualTo(FileDatasetParseStatus.QUEUED);

        Fixture missing = enqueue(
                "missing.csv", null, 3, Instant.now().minusSeconds(2)
        );
        assertThat(worker.runOne("test-worker-missing"))
                .isEqualTo(FileDatasetParseWorker.ExecutionOutcome.FAILED);
        assertThat(jobRepository.findById(missing.jobId()).orElseThrow().getStatus())
                .isEqualTo(FileDatasetParseJobStatus.FAILED);
        assertThat(tableRepository.existsById(missing.tableId())).isFalse();
        assertThat(fileRepository.existsById(missing.fileId())).isFalse();
    }

    @Test
    void recoversExpiredLeasesAndStopsWhenAttemptsAreExhausted() {
        Instant queuedAt = Instant.now().minusSeconds(180);
        Fixture retryable = enqueue("recover.csv", new byte[]{1}, 2, queuedAt);
        Fixture exhausted = enqueue("exhausted.csv", new byte[]{1}, 1, queuedAt.plusSeconds(1));
        makeExpired(retryable, "dead-worker-1");
        makeExpired(exhausted, "dead-worker-2");

        assertThat(coordinator.recoverExpiredLeases()).isEqualTo(2);

        assertThat(jobRepository.findById(retryable.jobId()).orElseThrow().getStatus())
                .isEqualTo(FileDatasetParseJobStatus.QUEUED);
        assertThat(tableRepository.findById(retryable.tableId()).orElseThrow().getParseStatus())
                .isEqualTo(FileDatasetParseStatus.QUEUED);
        assertThat(jobRepository.findById(exhausted.jobId()).orElseThrow().getStatus())
                .isEqualTo(FileDatasetParseJobStatus.FAILED);
        assertThat(tableRepository.existsById(exhausted.tableId())).isFalse();
        assertThat(fileRepository.existsById(exhausted.fileId())).isFalse();
    }

    @Test
    void rejectsAResultFromTheOldWorkerAfterItsExpiredLeaseIsRecovered() {
        Fixture fixture = enqueue(
                "late-result.csv", "id,name\n1,道路\n".getBytes(StandardCharsets.UTF_8),
                3, Instant.now().minusSeconds(180)
        );
        makeExpired(fixture, "old-worker");
        FileDatasetParseJobCoordinator.ClaimedJob oldClaim = new FileDatasetParseJobCoordinator.ClaimedJob(
                fixture.jobId(), "old-worker", FileDatasetParseJobType.TABLE_SOURCE_VALIDATE,
                fixture.datasetId(), fixture.fileId(), fixture.tableId(),
                new FileDatasetContentParser.Input(
                        FileDatasetFormat.CSV, FileDatasetCompression.NONE, "late-result.csv", 17,
                        CSV_OPTIONS, "FILE"
                ), null
        );

        assertThat(coordinator.recoverExpiredLeases()).isEqualTo(1);
        assertThat(coordinator.completeSuccess(oldClaim, new FileDatasetParser.ParseResult(
                List.of(new FileDatasetParser.Field(
                        "id",
                        0,
                        new PlatformTypeDefinition(PlatformDataType.LONG, null, null, null),
                        false
                )),
                List.of(Map.of("id", 1)),
                false
        ))).isFalse();

        assertThat(jobRepository.findById(fixture.jobId()).orElseThrow().getStatus())
                .isEqualTo(FileDatasetParseJobStatus.QUEUED);
        assertThat(tableRepository.findById(fixture.tableId()).orElseThrow().getParseStatus())
                .isEqualTo(FileDatasetParseStatus.QUEUED);
        assertThat(fieldRepository.findByFileDatasetTableIdOrderBySortOrderAsc(fixture.tableId()))
                .isEmpty();
    }

    @Test
    void heartbeatsOnlyTheWorkerThatOwnsTheActiveLease() {
        Fixture fixture = enqueue("heartbeat.csv", new byte[]{1}, 3, Instant.now().minusSeconds(2));
        FileDatasetParseJobCoordinator.ClaimedJob claimed = coordinator.claimNext("lease-owner").orElseThrow();
        Instant initialLeaseExpiry = jobRepository.findById(fixture.jobId()).orElseThrow().getLeaseExpiresAt();

        assertThat(coordinator.heartbeat(fixture.jobId(), "different-worker")).isFalse();
        assertThat(coordinator.heartbeat(fixture.jobId(), claimed.workerId())).isTrue();

        FileDatasetParseJob heartbeatJob = jobRepository.findById(fixture.jobId()).orElseThrow();
        assertThat(heartbeatJob.getLeaseOwner()).isEqualTo("lease-owner");
        assertThat(heartbeatJob.getLastHeartbeatAt()).isNotNull();
        assertThat(heartbeatJob.getLeaseExpiresAt()).isAfterOrEqualTo(initialLeaseExpiry);
    }

    @Test
    void neverDuplicatesALockedCandidateAndKeepsQueueOrderAfterRelease() throws Exception {
        Fixture first = enqueue("first.csv", new byte[]{1}, 3, Instant.now().minusSeconds(10));
        Fixture second = enqueue("second.csv", new byte[]{1}, 3, Instant.now().minusSeconds(5));
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> locked = executor.submit(() -> transactionTemplate.execute(status -> {
                FileDatasetParseJob job = queueRepository.lockNextAvailable(Instant.now()).orElseThrow();
                firstLocked.countDown();
                await(releaseFirst);
                return job.getId();
            }));
            assertThat(firstLocked.await(3, TimeUnit.SECONDS)).isTrue();

            Future<Optional<FileDatasetParseJobCoordinator.ClaimedJob>> claimed = executor.submit(
                    () -> coordinator.claimNext("skip-locked-worker")
            );
            Optional<FileDatasetParseJobCoordinator.ClaimedJob> result;
            try {
                result = claimed.get(3, TimeUnit.SECONDS);
            } finally {
                releaseFirst.countDown();
            }

            assertThat(locked.get(3, TimeUnit.SECONDS)).isEqualTo(first.jobId());
            // H2 applies LIMIT before SKIP LOCKED and therefore returns no row here. PostgreSQL's
            // production semantics skip the first row and return the second; that path needs the
            // opt-in PostgreSQL queue test. This default test still proves no duplicate claim.
            assertThat(result).isEmpty();
            assertThat(coordinator.claimNext("after-release-worker-1").orElseThrow().jobId())
                    .isEqualTo(first.jobId());
            assertThat(coordinator.claimNext("after-release-worker-2").orElseThrow().jobId())
                    .isEqualTo(second.jobId());
            assertThat(tableRepository.findById(second.tableId()).orElseThrow().getParseStatus())
                    .isEqualTo(FileDatasetParseStatus.PARSING);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private Fixture enqueue(String objectKey, byte[] content, int maxAttempts, Instant queuedAt) {
        if (content != null) {
            storage.put(objectKey, content);
        }
        return transactionTemplate.execute(status -> {
            FileDataset dataset = datasetRepository.saveAndFlush(FileDataset.create(
                    null, "队列测试-" + objectKey, FileDatasetType.CSV, CSV_OPTIONS, null
            ));
            FileDatasetFile file = fileRepository.saveAndFlush(FileDatasetFile.create(
                    dataset.getId(), objectKey, FileDatasetFormat.CSV, FileDatasetCompression.NONE,
                    objectKey, "text/csv", content == null ? 0 : content.length, null
            ));
            FileDatasetTable table = tableRepository.saveAndFlush(FileDatasetTable.create(
                    dataset.getId(),
                    "table_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                    objectKey
            ));
            FileDatasetParseJob job = jobRepository.saveAndFlush(FileDatasetParseJob.queueTableValidation(
                    dataset.getId(), file.getId(), table.getId(),
                    FileDatasetTableSourceLoadMode.INITIAL, null,
                    objectKey, "FILE", dataset.getName(), table.getName(), file.getOriginalFileName(),
                    maxAttempts, queuedAt
            ));
            table.queueInitialLoad(job.getId());
            tableRepository.saveAndFlush(table);
            return new Fixture(dataset.getId(), file.getId(), table.getId(), job.getId());
        });
    }

    private void makeExpired(Fixture fixture, String workerId) {
        transactionTemplate.executeWithoutResult(status -> {
            FileDatasetParseJob job = jobRepository.findLockedById(fixture.jobId()).orElseThrow();
            FileDatasetTable table = tableRepository.findLockedById(fixture.tableId()).orElseThrow();
            Instant claimTime = Instant.now().minusSeconds(120);
            job.claim(workerId, claimTime, claimTime.plusSeconds(60));
            table.startLoad(job.getId());
            jobRepository.save(job);
            tableRepository.save(table);
        });
    }

    private void clearData() {
        jobRepository.deleteAll();
        fieldRepository.deleteAll();
        sourceRepository.deleteAll();
        tableRepository.deleteAll();
        fileRepository.deleteAll();
        datasetRepository.deleteAll();
        storage.clear();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待并发领取测试锁超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发领取测试被中断", exception);
        }
    }

    private record Fixture(UUID datasetId, UUID fileId, UUID tableId, UUID jobId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StorageConfiguration {

        @Bean
        InMemoryQueueStorage fileObjectStorage() {
            return new InMemoryQueueStorage();
        }
    }

    static class InMemoryQueueStorage implements FileObjectStorage {

        private final Map<String, byte[]> values = new ConcurrentHashMap<>();
        private final AtomicBoolean failNextOpen = new AtomicBoolean();

        @Override
        public StoredFileObject store(
                String objectKey,
                InputStream inputStream,
                long contentLength,
                String contentType
        ) {
            try {
                values.put(objectKey, inputStream.readAllBytes());
                return new StoredFileObject("test-etag");
            } catch (IOException exception) {
                throw new FileStorageException("模拟对象存储写入失败", exception);
            }
        }

        @Override
        public FileObjectContent open(String objectKey) {
            assertFalse(
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    "后台解析不得在管理数据库事务中读取对象存储"
            );
            if (failNextOpen.compareAndSet(true, false)) {
                throw new FileStorageException("模拟对象存储瞬时失败", null);
            }
            byte[] content = values.get(objectKey);
            if (content == null) {
                throw new FileStorageObjectNotFoundException("模拟对象不存在", null);
            }
            return new FileObjectContent(
                    new ByteArrayInputStream(content), content.length, "text/csv"
            );
        }

        @Override
        public void delete(String objectKey) {
            values.remove(objectKey);
        }

        void put(String objectKey, byte[] content) {
            values.put(objectKey, content.clone());
        }

        void failNextOpen() {
            failNextOpen.set(true);
        }

        void clear() {
            values.clear();
            failNextOpen.set(false);
        }
    }
}
