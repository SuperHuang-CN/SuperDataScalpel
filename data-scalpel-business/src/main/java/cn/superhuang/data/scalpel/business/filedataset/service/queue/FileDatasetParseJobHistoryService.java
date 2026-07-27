package cn.superhuang.data.scalpel.business.filedataset.service.queue;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobStatus;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetParseJobRepository;
import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfigurationDefinition;
import cn.superhuang.data.scalpel.business.system.configuration.service.SystemConfigurationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class FileDatasetParseJobHistoryService {

    static final int BATCH_SIZE = 500;
    static final int MAX_BATCHES_PER_RUN = 20;
    private static final EnumSet<FileDatasetParseJobStatus> TERMINAL_STATUSES = EnumSet.of(
            FileDatasetParseJobStatus.SUCCEEDED,
            FileDatasetParseJobStatus.FAILED,
            FileDatasetParseJobStatus.CANCELLED
    );

    private final FileDatasetParseJobRepository jobRepository;
    private final SystemConfigurationService configurationService;
    private final TransactionTemplate transactionTemplate;

    public FileDatasetParseJobHistoryService(
            FileDatasetParseJobRepository jobRepository,
            SystemConfigurationService configurationService,
            PlatformTransactionManager transactionManager
    ) {
        this.jobRepository = jobRepository;
        this.configurationService = configurationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public int cleanExpiredHistory() {
        int retentionDays = configurationService.requireInteger(
                SystemConfigurationDefinition.FILE_DATASET_PARSING_HISTORY_RETENTION_DAYS
        );
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int totalDeleted = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int deleted = requireResult(transactionTemplate.execute(status -> deleteOneBatch(cutoff)));
            totalDeleted += deleted;
            if (deleted < BATCH_SIZE) {
                break;
            }
        }
        return totalDeleted;
    }

    private int deleteOneBatch(Instant cutoff) {
        List<UUID> ids = jobRepository.findExpiredTerminalIds(
                TERMINAL_STATUSES, cutoff, PageRequest.of(0, BATCH_SIZE)
        );
        if (ids.isEmpty()) {
            return 0;
        }
        jobRepository.deleteAllByIdInBatch(ids);
        jobRepository.flush();
        return ids.size();
    }

    private static int requireResult(Integer value) {
        if (value == null) {
            throw new IllegalStateException("解析任务历史清理事务未返回结果");
        }
        return value;
    }
}
