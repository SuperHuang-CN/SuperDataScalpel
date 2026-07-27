package cn.superhuang.data.scalpel.business.filedataset.service.queue;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDataset;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFile;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFileStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJob;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobType;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetStorageKind;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTable;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTableSourceLoadMode;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFileRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetParseJobRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableRepository;
import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfigurationDefinition;
import cn.superhuang.data.scalpel.business.system.configuration.service.SystemConfigurationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Transactional entry point shared by all file-dataset queue submissions and cancellations. */
@Service
public class FileDatasetParseJobSubmissionService {

    private final FileDatasetTableRepository tableRepository;
    private final FileDatasetFileRepository fileRepository;
    private final FileDatasetParseJobRepository jobRepository;
    private final SystemConfigurationService configurationService;

    public FileDatasetParseJobSubmissionService(
            FileDatasetTableRepository tableRepository,
            FileDatasetFileRepository fileRepository,
            FileDatasetParseJobRepository jobRepository,
            SystemConfigurationService configurationService
    ) {
        this.tableRepository = tableRepository;
        this.fileRepository = fileRepository;
        this.jobRepository = jobRepository;
        this.configurationService = configurationService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Submission enqueueTableValidation(
            FileDataset dataset,
            FileDatasetFile file,
            FileDatasetTable table,
            FileDatasetTableSourceLoadMode loadMode,
            UUID targetSourceId,
            String sourceName,
            String sourceKey
    ) {
        requireOwnership(dataset, file, table);
        if (file.getStatus() != FileDatasetFileStatus.READY) {
            throw conflict("来源文件尚未准备完成");
        }
        if (table.getCurrentLoadJobId() != null) {
            throw conflict("逻辑表已经存在正在执行的数据装载");
        }
        FileDatasetParseJob job = jobRepository.saveAndFlush(FileDatasetParseJob.queueTableValidation(
                dataset.getId(), file.getId(), table.getId(), loadMode, targetSourceId,
                sourceName, sourceKey, dataset.getName(), table.getName(), file.getOriginalFileName(),
                maxAttempts(), Instant.now()
        ));
        if (loadMode == FileDatasetTableSourceLoadMode.INITIAL) {
            table.queueInitialLoad(job.getId());
        } else {
            table.beginDataChange(job.getId());
        }
        tableRepository.saveAndFlush(table);
        return new Submission(job, file, table);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public FilePreparationSubmission enqueuePreparation(
            FileDataset dataset,
            FileDatasetFile file,
            FileDatasetTable table,
            FileDatasetTableSourceLoadMode loadMode,
            UUID targetSourceId,
            String sourceName,
            String sourceKey
    ) {
        if (!dataset.getId().equals(file.getFileDatasetId())) {
            throw new IllegalArgumentException("准备任务中的文件不属于目标文件数据集");
        }
        if (file.getStorageKind() == FileDatasetStorageKind.SINGLE_OBJECT) {
            throw conflict("当前文件不需要准备任务");
        }
        if (file.getStatus() != FileDatasetFileStatus.PREPARING || file.getCurrentPreparationJobId() != null) {
            throw conflict("文件当前不能提交准备任务");
        }
        if (table != null) {
            requireOwnership(dataset, file, table);
            if (table.getCurrentLoadJobId() != null) {
                throw conflict("逻辑表已经存在正在执行的数据装载");
            }
        }
        FileDatasetParseJob job = jobRepository.saveAndFlush(FileDatasetParseJob.queueFilePreparation(
                dataset.getId(), file.getId(), table == null ? null : table.getId(),
                loadMode, targetSourceId, sourceName, sourceKey,
                dataset.getName(), table == null ? null : table.getName(), file.getOriginalFileName(),
                maxAttempts(), Instant.now()
        ));
        file.queuePreparation(job.getId());
        fileRepository.saveAndFlush(file);
        if (table != null) {
            if (loadMode == FileDatasetTableSourceLoadMode.INITIAL) {
                table.queueInitialLoad(job.getId());
            } else {
                table.beginDataChange(job.getId());
            }
            tableRepository.saveAndFlush(table);
        }
        return new FilePreparationSubmission(job, file, table);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public FilePreparationSubmission enqueuePreparation(FileDataset dataset, FileDatasetFile file) {
        return enqueuePreparation(dataset, file, null, null, null, null, null);
    }

    /**
     * Cancels a queued preparation before a caller deletes or replaces its file. Running work is
     * deliberately rejected because the worker still owns an object-store operation.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public FileDatasetFile cancelQueuedPreparationAndLockFile(
            FileDatasetFile initialFile,
            String reason,
            String runningConflictMessage
    ) {
        FileDatasetFile file = fileRepository.findLockedById(initialFile.getId())
                .orElseThrow(() -> conflict("文件准备状态已经变化，请稍后重试"));
        UUID jobId = file.getCurrentPreparationJobId();
        if (jobId == null) {
            return file;
        }
        FileDatasetParseJob job = jobRepository.findLockedById(jobId)
                .orElseThrow(() -> conflict("文件准备状态已经变化，请稍后重试"));
        if (!matchesCurrentPreparation(file, job)) {
            throw conflict("文件准备状态已经变化，请稍后重试");
        }
        if (job.getStatus() == FileDatasetParseJobStatus.RUNNING) {
            throw conflict(runningConflictMessage);
        }
        if (job.getStatus() != FileDatasetParseJobStatus.QUEUED) {
            throw conflict("文件准备状态已经变化，请稍后重试");
        }
        job.cancel(reason, Instant.now());
        file.cancelQueuedPreparation(job.getId());
        clearTableIfCurrent(job);
        jobRepository.save(job);
        fileRepository.save(file);
        return file;
    }

    /** Cancels queued validation jobs and returns locked table rows for a following mutation. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<FileDatasetTable> cancelQueuedAndLockTables(
            List<FileDatasetTable> tables,
            String reason,
            String runningConflictMessage
    ) {
        Map<UUID, FileDatasetTable> result = new HashMap<>();
        for (UUID tableId : tables.stream().map(FileDatasetTable::getId).sorted().toList()) {
            FileDatasetTable table = tableRepository.findLockedById(tableId)
                    .orElseThrow(() -> conflict("逻辑表状态已经变化，请稍后重试"));
            UUID jobId = table.getCurrentLoadJobId();
            if (jobId != null) {
                FileDatasetParseJob job = jobRepository.findLockedById(jobId)
                        .orElseThrow(() -> conflict("表装载状态已经变化，请稍后重试"));
                if (!tableId.equals(job.getFileDatasetTableId())) {
                    throw conflict("表装载状态已经变化，请稍后重试");
                }
                if (job.getStatus() == FileDatasetParseJobStatus.RUNNING) {
                    throw conflict(runningConflictMessage);
                }
                if (job.getStatus() != FileDatasetParseJobStatus.QUEUED) {
                    throw conflict("表装载状态已经变化，请稍后重试");
                }
                job.cancel(reason, Instant.now());
                table.clearCurrentLoad(jobId);
                jobRepository.save(job);
                tableRepository.save(table);
            }
            result.put(tableId, table);
        }
        List<FileDatasetTable> ordered = new ArrayList<>(tables.size());
        tables.forEach(table -> ordered.add(result.get(table.getId())));
        return List.copyOf(ordered);
    }

    private void clearTableIfCurrent(FileDatasetParseJob job) {
        if (job.getFileDatasetTableId() == null) {
            return;
        }
        FileDatasetTable table = tableRepository.findLockedById(job.getFileDatasetTableId()).orElse(null);
        if (table != null && job.getId().equals(table.getCurrentLoadJobId())) {
            table.clearCurrentLoad(job.getId());
            tableRepository.save(table);
        }
    }

    private int maxAttempts() {
        return configurationService.requireInteger(SystemConfigurationDefinition.FILE_DATASET_PARSING_MAX_ATTEMPTS);
    }

    private static void requireOwnership(FileDataset dataset, FileDatasetFile file, FileDatasetTable table) {
        if (!dataset.getId().equals(file.getFileDatasetId())
                || !dataset.getId().equals(table.getFileDatasetId())) {
            throw new IllegalArgumentException("文件或逻辑表不属于目标文件数据集");
        }
    }

    private static boolean matchesCurrentPreparation(FileDatasetFile file, FileDatasetParseJob job) {
        return job.getType() == FileDatasetParseJobType.FILE_PREPARATION
                && file.getStatus() == FileDatasetFileStatus.PREPARING
                && job.getId().equals(file.getCurrentPreparationJobId())
                && file.getId().equals(job.getSourceFileId())
                && file.getFileDatasetId().equals(job.getFileDatasetId());
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record Submission(FileDatasetParseJob job, FileDatasetFile file, FileDatasetTable table) {
    }

    public record FilePreparationSubmission(
            FileDatasetParseJob job,
            FileDatasetFile file,
            FileDatasetTable table
    ) {
    }
}
