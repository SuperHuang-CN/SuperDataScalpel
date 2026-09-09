package cn.superhuang.data.scalpel.business.filedataset.service.queue;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDataset;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetField;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFile;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFileStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJob;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobType;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetStorageKind;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTable;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTableSource;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTableSourceLoadMode;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFieldRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFileRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetParseJobQueueRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetParseJobRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableSourceRepository;
import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetParsedMetadata;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetContentParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetSchemaValidator;
import cn.superhuang.data.scalpel.business.filedataset.service.prepare.FileDatasetPreparationService;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Coordinates the durable queue while keeping all database mutations in short transactions. */
@Component
public class FileDatasetParseJobCoordinator {

    private static final Logger log = LoggerFactory.getLogger(FileDatasetParseJobCoordinator.class);
    static final Duration LEASE_DURATION = Duration.ofSeconds(60);
    private static final String INVALID_TASK_ERROR = "解析任务与当前文件数据集状态不一致";
    private static final String EXPIRED_LEASE_ERROR = "Worker 心跳超时，解析任务租约已过期";
    private static final Set<FileDatasetParseJobStatus> NON_TERMINAL =
            Set.of(FileDatasetParseJobStatus.QUEUED, FileDatasetParseJobStatus.RUNNING);

    private final FileDatasetParseJobQueueRepository queueRepository;
    private final FileDatasetParseJobRepository jobRepository;
    private final FileDatasetRepository datasetRepository;
    private final FileDatasetFileRepository fileRepository;
    private final FileDatasetTableRepository tableRepository;
    private final FileDatasetTableSourceRepository sourceRepository;
    private final FileDatasetFieldRepository fieldRepository;
    private final FileDatasetParseRetryPolicy retryPolicy;
    private final FileDatasetSchemaValidator schemaValidator;
    private final ObjectProvider<FileObjectStorage> storageProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public FileDatasetParseJobCoordinator(
            FileDatasetParseJobQueueRepository queueRepository,
            FileDatasetParseJobRepository jobRepository,
            FileDatasetRepository datasetRepository,
            FileDatasetFileRepository fileRepository,
            FileDatasetTableRepository tableRepository,
            FileDatasetTableSourceRepository sourceRepository,
            FileDatasetFieldRepository fieldRepository,
            FileDatasetParseRetryPolicy retryPolicy,
            FileDatasetSchemaValidator schemaValidator,
            ObjectProvider<FileObjectStorage> storageProvider,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.queueRepository = queueRepository;
        this.jobRepository = jobRepository;
        this.datasetRepository = datasetRepository;
        this.fileRepository = fileRepository;
        this.tableRepository = tableRepository;
        this.sourceRepository = sourceRepository;
        this.fieldRepository = fieldRepository;
        this.retryPolicy = retryPolicy;
        this.schemaValidator = schemaValidator;
        this.storageProvider = storageProvider;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Optional<ClaimedJob> claimNext(String workerId) {
        return requireResult(transactionTemplate.execute(status -> claimNextInTransaction(workerId)));
    }

    public boolean heartbeat(UUID jobId, String workerId) {
        return requireResult(transactionTemplate.execute(status -> {
            FileDatasetParseJob job = jobRepository.findLockedById(jobId).orElse(null);
            Instant now = Instant.now();
            if (!hasActiveLease(job, workerId, now)) {
                return false;
            }
            job.heartbeat(workerId, now, now.plus(LEASE_DURATION));
            jobRepository.saveAndFlush(job);
            return true;
        }));
    }

    public boolean completeTableSuccess(ClaimedJob claimedJob, FileDatasetParser.ParseResult result) {
        if (claimedJob.type() != FileDatasetParseJobType.TABLE_SOURCE_VALIDATE || result.fields().isEmpty()) {
            throw new IllegalArgumentException("表来源校验成功结果无效");
        }
        String parsedMetadata = writeParsedMetadata(result);
        String sourceMetadata = writeSourceMetadata(result.sourceMetadata());
        String fingerprint = schemaValidator.fingerprint(result.fields());
        return requireResult(transactionTemplate.execute(status -> {
            FileDatasetParseJob job = jobRepository.findLockedById(claimedJob.jobId()).orElse(null);
            Instant now = Instant.now();
            if (!hasActiveLease(job, claimedJob.workerId(), now)) {
                return false;
            }
            FileDatasetTable table = tableRepository.findLockedById(job.getFileDatasetTableId()).orElse(null);
            FileDatasetFile file = fileRepository.findLockedById(job.getSourceFileId()).orElse(null);
            if (!matchesCurrentTableJob(table, file, job)) {
                job.fail(claimedJob.workerId(), INVALID_TASK_ERROR, now);
                jobRepository.saveAndFlush(job);
                removeFailedTemporaryState(job);
                return false;
            }

            List<FileDatasetTableSource> current =
                    sourceRepository.findByFileDatasetTableIdOrderBySourceOrderAsc(table.getId());
            if (job.getLoadMode() == FileDatasetTableSourceLoadMode.INITIAL) {
                if (!current.isEmpty()) {
                    return rejectCurrentJob(job, table, claimedJob.workerId(), now);
                }
                fieldRepository.saveAll(result.fields().stream().map(field -> FileDatasetField.create(
                        table.getId(), field.name(), field.sortOrder(), field.type(), field.nullable()
                )).toList());
                sourceRepository.save(FileDatasetTableSource.create(
                        table.getId(), file.getId(), job.getSourceName(), job.getSourceKey(), 0,
                        result.rowCount(), fingerprint, sourceMetadata, now
                ));
                table.completeInitialLoad(job.getId(), parsedMetadata, result.previewSupported());
            } else {
                if (current.isEmpty()) {
                    return rejectCurrentJob(job, table, claimedJob.workerId(), now);
                }
                List<FileDatasetField> expected =
                        fieldRepository.findByFileDatasetTableIdOrderBySortOrderAsc(table.getId());
                schemaValidator.requireCompatible(expected, result, canonicalSourceMetadata(current));
                applyCurrentDataChange(job, table, file, current, result, fingerprint, sourceMetadata, now);
                boolean previewSupported = job.getLoadMode() == FileDatasetTableSourceLoadMode.REPLACE_ALL
                        ? result.previewSupported()
                        : table.getParseStatus() == FileDatasetParseStatus.READY && result.previewSupported();
                table.completeDataChange(job.getId(), parsedMetadata, previewSupported);
            }
            job.succeed(claimedJob.workerId(), now);
            tableRepository.save(table);
            jobRepository.save(job);
            fieldRepository.flush();
            sourceRepository.flush();
            tableRepository.flush();
            jobRepository.flush();
            return true;
        }));
    }

    public boolean completeSuccess(ClaimedJob claimedJob, FileDatasetParser.ParseResult result) {
        return completeTableSuccess(claimedJob, result);
    }

    public boolean completePreparationSuccess(
            ClaimedJob claimedJob,
            FileDatasetPreparationService.FileDatasetPreparationResult result
    ) {
        if (claimedJob.type() != FileDatasetParseJobType.FILE_PREPARATION || result.tables().isEmpty()) {
            throw new IllegalArgumentException("文件准备成功结果无效");
        }
        return requireResult(transactionTemplate.execute(status -> {
            FileDatasetParseJob job = jobRepository.findLockedById(claimedJob.jobId()).orElse(null);
            Instant now = Instant.now();
            if (!hasActiveLease(job, claimedJob.workerId(), now)) {
                return false;
            }
            FileDatasetFile file = fileRepository.findLockedById(job.getSourceFileId()).orElse(null);
            FileDataset dataset = datasetRepository.findById(job.getFileDatasetId()).orElse(null);
            FileDatasetTable table = job.getFileDatasetTableId() == null
                    ? null : tableRepository.findLockedById(job.getFileDatasetTableId()).orElse(null);
            if (dataset == null || !matchesPreparingFile(file, job)
                    || (table != null && !job.getId().equals(table.getCurrentLoadJobId()))) {
                job.fail(claimedJob.workerId(), INVALID_TASK_ERROR, now);
                jobRepository.saveAndFlush(job);
                removeFailedTemporaryState(job);
                return false;
            }
            if (result.materializedPrefix() == null) {
                file.completePreparationWithoutMaterialization(job.getId());
            } else {
                file.completePreparation(
                        job.getId(), result.materializedPrefix(), result.materializedSizeBytes(), result.materializedEntryCount()
                );
            }
            job.succeed(claimedJob.workerId(), now);
            fileRepository.save(file);
            jobRepository.save(job);
            fileRepository.flush();
            jobRepository.flush();

            if (table != null) {
                FileDatasetParseJob validation = createValidationJob(
                        dataset, file, table, job.getLoadMode(), job.getTargetSourceId(),
                        job.getSourceName(), job.getSourceKey(), job.getMaxAttempts()
                );
                table.handoffCurrentLoad(job.getId(), validation.getId());
                tableRepository.saveAndFlush(table);
                return true;
            }

            Set<String> usedCodes = new HashSet<>();
            tableRepository.findByFileDatasetIdOrderByCreatedAtAsc(dataset.getId())
                    .forEach(existing -> usedCodes.add(existing.getCode()));
            for (FileDatasetPreparationService.DiscoveredTable discovered : result.tables()) {
                FileDatasetTable discoveredTable = tableRepository.saveAndFlush(FileDatasetTable.create(
                        dataset.getId(), uniqueCode(discovered.sourceName(), usedCodes), discovered.sourceName()
                ));
                FileDatasetParseJob validation = createValidationJob(
                        dataset, file, discoveredTable, FileDatasetTableSourceLoadMode.INITIAL, null,
                        discovered.sourceName(), discovered.sourceKey(), job.getMaxAttempts()
                );
                discoveredTable.queueInitialLoad(validation.getId());
                tableRepository.saveAndFlush(discoveredTable);
            }
            return true;
        }));
    }

    public FailureOutcome completeFailure(
            ClaimedJob claimedJob,
            FileDatasetParseFailureClassifier.Failure failure
    ) {
        int baseDelaySeconds = failure.retryable() ? retryPolicy.currentBaseDelaySeconds() : 1;
        return requireResult(transactionTemplate.execute(status -> {
            FileDatasetParseJob job = jobRepository.findLockedById(claimedJob.jobId()).orElse(null);
            Instant now = Instant.now();
            if (!hasActiveLease(job, claimedJob.workerId(), now)) {
                return FailureOutcome.STALE;
            }
            return finishOrRetry(job, claimedJob.workerId(), failure, baseDelaySeconds, now);
        }));
    }

    public int recoverExpiredLeases() {
        int baseDelaySeconds = retryPolicy.currentBaseDelaySeconds();
        return requireResult(transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            List<FileDatasetParseJob> jobs = queueRepository.lockExpiredLeases(now);
            for (FileDatasetParseJob job : jobs) {
                recoverExpired(job, now, baseDelaySeconds);
            }
            return jobs.size();
        }));
    }

    private FailureOutcome finishOrRetry(
            FileDatasetParseJob job,
            String workerId,
            FileDatasetParseFailureClassifier.Failure failure,
            int baseDelaySeconds,
            Instant now
    ) {
        String error = truncateError(failure.message());
        if (failure.retryable() && job.canRetry()) {
            job.retry(workerId, error, now,
                    retryPolicy.nextAvailableAt(now, job.getAttemptCount(), baseDelaySeconds));
            requeueCurrentTable(job);
            jobRepository.saveAndFlush(job);
            return FailureOutcome.REQUEUED;
        }
        job.fail(workerId, error, now);
        jobRepository.saveAndFlush(job);
        removeFailedTemporaryState(job);
        return FailureOutcome.FAILED;
    }

    private void recoverExpired(FileDatasetParseJob job, Instant now, int baseDelaySeconds) {
        if (!matchesCurrentJob(job)) {
            job.failExpiredLease(now, INVALID_TASK_ERROR);
            jobRepository.saveAndFlush(job);
            removeFailedTemporaryState(job);
            return;
        }
        boolean retry = job.canRetry();
        job.recoverExpiredLease(
                now,
                retryPolicy.nextAvailableAt(now, job.getAttemptCount(), baseDelaySeconds),
                EXPIRED_LEASE_ERROR
        );
        jobRepository.saveAndFlush(job);
        if (retry) {
            requeueCurrentTable(job);
        } else {
            removeFailedTemporaryState(job);
        }
    }

    private Optional<ClaimedJob> claimNextInTransaction(String workerId) {
        Instant now = Instant.now();
        Optional<FileDatasetParseJob> candidate = queueRepository.lockNextAvailable(now);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        FileDatasetParseJob job = candidate.orElseThrow();
        job.claim(workerId, now, now.plus(LEASE_DURATION));
        return job.getType() == FileDatasetParseJobType.FILE_PREPARATION
                ? claimPreparation(job, workerId, now)
                : claimValidation(job, workerId, now);
    }

    private Optional<ClaimedJob> claimPreparation(FileDatasetParseJob job, String workerId, Instant now) {
        FileDataset dataset = datasetRepository.findById(job.getFileDatasetId()).orElse(null);
        FileDatasetFile file = fileRepository.findLockedById(job.getSourceFileId()).orElse(null);
        FileDatasetTable table = job.getFileDatasetTableId() == null
                ? null : tableRepository.findLockedById(job.getFileDatasetTableId()).orElse(null);
        if (dataset == null || !matchesPreparingFile(file, job)
                || (job.getFileDatasetTableId() != null
                    && (table == null || !job.getId().equals(table.getCurrentLoadJobId())))) {
            job.fail(workerId, INVALID_TASK_ERROR, now);
            jobRepository.saveAndFlush(job);
            removeFailedTemporaryState(job);
            return Optional.empty();
        }
        if (table != null) {
            table.startLoad(job.getId());
            tableRepository.save(table);
        }
        jobRepository.saveAndFlush(job);
        String suffix = file.getFormat() == FileDatasetFormat.GDB ? ".gdb" : "";
        String materializedPrefix = "file-datasets/materialized/" + file.getId() + "/"
                + job.getId() + suffix;
        return Optional.of(new ClaimedJob(
                job.getId(), workerId, job.getType(), job.getFileDatasetId(),
                job.getSourceFileId(), job.getFileDatasetTableId(), null,
                new FileDatasetPreparationService.FileDatasetPreparationInput(
                        file.getFormat(), file.getObjectKey(), file.getSizeBytes(), materializedPrefix,
                        dataset.getParsingOptions()
                )
        ));
    }

    private Optional<ClaimedJob> claimValidation(FileDatasetParseJob job, String workerId, Instant now) {
        FileDataset dataset = datasetRepository.findById(job.getFileDatasetId()).orElse(null);
        FileDatasetFile file = fileRepository.findById(job.getSourceFileId()).orElse(null);
        FileDatasetTable table = tableRepository.findLockedById(job.getFileDatasetTableId()).orElse(null);
        if (dataset == null || !matchesCurrentTableJob(table, file, job)) {
            job.fail(workerId, INVALID_TASK_ERROR, now);
            jobRepository.saveAndFlush(job);
            removeFailedTemporaryState(job);
            return Optional.empty();
        }
        table.startLoad(job.getId());
        tableRepository.save(table);
        jobRepository.saveAndFlush(job);
        String objectKey = file.getStorageKind() == FileDatasetStorageKind.SINGLE_OBJECT
                ? file.getObjectKey() : file.getMaterializedPrefix();
        return Optional.of(new ClaimedJob(
                job.getId(), workerId, job.getType(), job.getFileDatasetId(),
                job.getSourceFileId(), job.getFileDatasetTableId(),
                new FileDatasetContentParser.Input(
                        file.getFormat(), file.getCompression(), objectKey, file.getSizeBytes(),
                        dataset.getParsingOptions(), job.getSourceKey()
                ),
                null
        ));
    }

    private void applyCurrentDataChange(
            FileDatasetParseJob job,
            FileDatasetTable table,
            FileDatasetFile file,
            List<FileDatasetTableSource> current,
            FileDatasetParser.ParseResult result,
            String fingerprint,
            String sourceMetadata,
            Instant now
    ) {
        switch (job.getLoadMode()) {
            case APPEND -> sourceRepository.save(FileDatasetTableSource.create(
                    table.getId(), file.getId(), job.getSourceName(), job.getSourceKey(), current.size(),
                    result.rowCount(), fingerprint, sourceMetadata, now
            ));
            case REPLACE_ALL -> {
                Set<UUID> oldFileIds = current.stream()
                        .map(FileDatasetTableSource::getSourceFileId)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                sourceRepository.deleteAll(current);
                sourceRepository.flush();
                sourceRepository.save(FileDatasetTableSource.create(
                        table.getId(), file.getId(), job.getSourceName(), job.getSourceKey(), 0,
                        result.rowCount(), fingerprint, sourceMetadata, now
                ));
                oldFileIds.forEach(this::deleteFileIfOrphaned);
            }
            case REPLACE_SOURCE -> {
                FileDatasetTableSource target = current.stream()
                        .filter(source -> source.getId().equals(job.getTargetSourceId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("被替换的当前来源不存在"));
                UUID oldFileId = target.getSourceFileId();
                target.replace(
                        file.getId(), job.getSourceName(), job.getSourceKey(), result.rowCount(),
                        fingerprint, sourceMetadata, now
                );
                sourceRepository.saveAndFlush(target);
                deleteFileIfOrphaned(oldFileId);
            }
            case INITIAL -> throw new IllegalStateException("初始装载不能进入当前数据变更流程");
        }
    }

    private boolean rejectCurrentJob(
            FileDatasetParseJob job,
            FileDatasetTable table,
            String workerId,
            Instant now
    ) {
        job.fail(workerId, INVALID_TASK_ERROR, now);
        table.clearCurrentLoad(job.getId());
        jobRepository.save(job);
        tableRepository.save(table);
        return false;
    }

    private FileDatasetParseJob createValidationJob(
            FileDataset dataset,
            FileDatasetFile file,
            FileDatasetTable table,
            FileDatasetTableSourceLoadMode loadMode,
            UUID targetSourceId,
            String sourceName,
            String sourceKey,
            int maxAttempts
    ) {
        return jobRepository.saveAndFlush(FileDatasetParseJob.queueTableValidation(
                dataset.getId(), file.getId(), table.getId(), loadMode, targetSourceId,
                sourceName, sourceKey, dataset.getName(), table.getName(), file.getOriginalFileName(),
                maxAttempts, Instant.now()
        ));
    }

    private void requeueCurrentTable(FileDatasetParseJob job) {
        if (job.getFileDatasetTableId() == null) {
            return;
        }
        FileDatasetTable table = tableRepository.findLockedById(job.getFileDatasetTableId()).orElse(null);
        if (table != null && job.getId().equals(table.getCurrentLoadJobId())) {
            table.requeueLoad(job.getId());
            tableRepository.saveAndFlush(table);
        }
    }

    private void removeFailedTemporaryState(FileDatasetParseJob job) {
        if (job.getFileDatasetTableId() != null) {
            FileDatasetTable table = tableRepository.findLockedById(job.getFileDatasetTableId()).orElse(null);
            if (table != null && job.getId().equals(table.getCurrentLoadJobId())) {
                table.clearCurrentLoad(job.getId());
                if (job.getLoadMode() == FileDatasetTableSourceLoadMode.INITIAL
                        && sourceRepository.countByFileDatasetTableId(table.getId()) == 0) {
                    fieldRepository.deleteByFileDatasetTableId(table.getId());
                    tableRepository.delete(table);
                } else {
                    tableRepository.save(table);
                }
                tableRepository.flush();
            }
        }
        deleteFileIfOrphaned(job.getSourceFileId());
    }

    private void deleteFileIfOrphaned(UUID fileId) {
        FileDatasetFile file = fileRepository.findLockedById(fileId).orElse(null);
        if (file == null
                || sourceRepository.existsBySourceFileId(fileId)
                || jobRepository.existsBySourceFileIdAndStatusIn(fileId, NON_TERMINAL)) {
            return;
        }
        String objectKey = file.getObjectKey();
        String materializedPrefix = file.getMaterializedPrefix();
        fileRepository.delete(file);
        fileRepository.flush();
        afterCommit(() -> {
            FileObjectStorage storage = storageProvider.getIfAvailable();
            if (storage == null) {
                log.warn("文件记录已删除，但对象存储不可用，可能残留孤儿对象：{}", objectKey);
                return;
            }
            deleteObjectQuietly(storage, objectKey);
            deletePrefixQuietly(storage, materializedPrefix);
        });
    }

    private boolean matchesCurrentJob(FileDatasetParseJob job) {
        FileDatasetFile file = fileRepository.findById(job.getSourceFileId()).orElse(null);
        if (job.getType() == FileDatasetParseJobType.FILE_PREPARATION) {
            return matchesPreparingFile(file, job);
        }
        FileDatasetTable table = tableRepository.findById(job.getFileDatasetTableId()).orElse(null);
        return matchesCurrentTableJob(table, file, job);
    }

    private static boolean matchesCurrentTableJob(
            FileDatasetTable table,
            FileDatasetFile file,
            FileDatasetParseJob job
    ) {
        return table != null
                && file != null
                && file.getStatus() == FileDatasetFileStatus.READY
                && job.getType() == FileDatasetParseJobType.TABLE_SOURCE_VALIDATE
                && job.getId().equals(table.getCurrentLoadJobId())
                && job.getFileDatasetTableId().equals(table.getId())
                && job.getFileDatasetId().equals(table.getFileDatasetId())
                && job.getSourceFileId().equals(file.getId())
                && job.getFileDatasetId().equals(file.getFileDatasetId())
                && (file.getStorageKind() == FileDatasetStorageKind.SINGLE_OBJECT
                    || file.getMaterializedPrefix() != null);
    }

    private static boolean matchesPreparingFile(FileDatasetFile file, FileDatasetParseJob job) {
        return file != null
                && job != null
                && job.getType() == FileDatasetParseJobType.FILE_PREPARATION
                && file.getStatus() == FileDatasetFileStatus.PREPARING
                && job.getId().equals(file.getCurrentPreparationJobId())
                && file.getId().equals(job.getSourceFileId())
                && file.getFileDatasetId().equals(job.getFileDatasetId());
    }

    private static boolean hasActiveLease(FileDatasetParseJob job, String workerId, Instant now) {
        return job != null
                && job.getStatus() == FileDatasetParseJobStatus.RUNNING
                && workerId != null
                && workerId.trim().equals(job.getLeaseOwner())
                && job.getLeaseExpiresAt() != null
                && job.getLeaseExpiresAt().isAfter(now);
    }

    private Map<String, Object> canonicalSourceMetadata(List<FileDatasetTableSource> sources) {
        return sources.isEmpty() ? Map.of() : readSourceMetadata(sources.getFirst().getSourceMetadata());
    }

    private String writeParsedMetadata(FileDatasetParser.ParseResult result) {
        try {
            return objectMapper.writeValueAsString(new FileDatasetParsedMetadata(
                    result.rows().size(), result.truncated(), result.sourceMetadata()
            ));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法保存文件解析元数据", exception);
        }
    }

    private String writeSourceMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(metadata));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法保存文件来源元数据", exception);
        }
    }

    private Map<String, Object> readSourceMetadata(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (RuntimeException exception) {
            throw new IllegalStateException("已保存的文件来源元数据无效", exception);
        }
    }

    private static String uniqueCode(String name, Set<String> usedCodes) {
        String normalized = name.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "_")
                .replaceAll("^_+|_+$", "");
        String base = normalized.isEmpty() ? "table" : normalized.substring(0, Math.min(100, normalized.length()));
        String candidate = base;
        int suffix = 2;
        while (!usedCodes.add(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private static String truncateError(String errorMessage) {
        String normalized = errorMessage == null || errorMessage.isBlank()
                ? "文件解析失败" : errorMessage.trim();
        return normalized.length() <= FileDatasetParseJob.MAX_ERROR_MESSAGE_LENGTH
                ? normalized : normalized.substring(0, FileDatasetParseJob.MAX_ERROR_MESSAGE_LENGTH);
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private static void deleteObjectQuietly(FileObjectStorage storage, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            storage.delete(objectKey);
        } catch (RuntimeException exception) {
            log.warn("文件对象删除失败，已保留日志供人工清理：{}", objectKey, exception);
        }
    }

    private static void deletePrefixQuietly(FileObjectStorage storage, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        try {
            storage.deletePrefix(prefix);
        } catch (RuntimeException exception) {
            log.warn("文件物化目录删除失败，已保留日志供人工清理：{}", prefix, exception);
        }
    }

    private static <T> T requireResult(T result) {
        if (result == null) {
            throw new IllegalStateException("解析任务事务未返回预期结果");
        }
        return result;
    }

    public record ClaimedJob(
            UUID jobId,
            String workerId,
            FileDatasetParseJobType type,
            UUID datasetId,
            UUID sourceFileId,
            UUID tableId,
            FileDatasetContentParser.Input input,
            FileDatasetPreparationService.FileDatasetPreparationInput preparationInput
    ) {
    }

    public enum FailureOutcome {
        REQUEUED,
        FAILED,
        STALE
    }
}
