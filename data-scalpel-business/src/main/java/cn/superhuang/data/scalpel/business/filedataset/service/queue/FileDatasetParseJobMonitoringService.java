package cn.superhuang.data.scalpel.business.filedataset.service.queue;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJob;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobStatus;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetParseJobRepository;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetParseJobResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetParseQueueSummaryResponse;
import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfigurationDefinition;
import cn.superhuang.data.scalpel.business.system.configuration.service.SystemConfigurationService;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class FileDatasetParseJobMonitoringService {

    private final FileDatasetParseJobRepository jobRepository;
    private final SystemConfigurationService configurationService;
    private final SearchEngine searchEngine;

    public FileDatasetParseJobMonitoringService(
            FileDatasetParseJobRepository jobRepository,
            SystemConfigurationService configurationService,
            SearchEngine searchEngine
    ) {
        this.jobRepository = jobRepository;
        this.configurationService = configurationService;
        this.searchEngine = searchEngine;
    }

    @Transactional(readOnly = true)
    public FileDatasetParseQueueSummaryResponse summary() {
        Instant now = Instant.now();
        long queuedCount = jobRepository.countByStatus(FileDatasetParseJobStatus.QUEUED);
        long runnableQueuedCount = jobRepository.countByStatusAndAvailableAtLessThanEqual(
                FileDatasetParseJobStatus.QUEUED, now
        );
        return new FileDatasetParseQueueSummaryResponse(
                configurationService.requireBoolean(
                        SystemConfigurationDefinition.FILE_DATASET_PARSING_QUEUE_ENABLED
                ),
                configurationService.requireInteger(
                        SystemConfigurationDefinition.FILE_DATASET_PARSING_WORKER_CONCURRENCY
                ),
                configurationService.requireInteger(
                        SystemConfigurationDefinition.FILE_DATASET_PARSING_HISTORY_RETENTION_DAYS
                ),
                queuedCount,
                runnableQueuedCount,
                Math.max(0, queuedCount - runnableQueuedCount),
                jobRepository.countByStatus(FileDatasetParseJobStatus.RUNNING),
                jobRepository.countByStatus(FileDatasetParseJobStatus.SUCCEEDED),
                jobRepository.countByStatus(FileDatasetParseJobStatus.FAILED),
                jobRepository.countByStatus(FileDatasetParseJobStatus.CANCELLED),
                jobRepository.findOldestQueuedAt(FileDatasetParseJobStatus.QUEUED).orElse(null),
                jobRepository.findOldestStartedAt(FileDatasetParseJobStatus.RUNNING).orElse(null),
                now
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<FileDatasetParseJobResponse> search(SearchRequest request) {
        Page<FileDatasetParseJob> result = searchEngine.search(
                request, FileDatasetParseJob.class, jobRepository
        );
        return new PageResponse<>(
                result.getContent().stream().map(FileDatasetParseJobResponse::from).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize()
        );
    }
}
