package cn.superhuang.data.scalpel.business.filedataset.service.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "data-scalpel.file-parsing",
        name = "background-worker-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class FileDatasetParseJobHistoryScheduler {

    static final long INITIAL_DELAY_MILLISECONDS = 5 * 60 * 1_000L;
    static final long CLEANUP_INTERVAL_MILLISECONDS = 60 * 60 * 1_000L;
    private static final Logger log = LoggerFactory.getLogger(FileDatasetParseJobHistoryScheduler.class);

    private final FileDatasetParseJobHistoryService historyService;

    public FileDatasetParseJobHistoryScheduler(FileDatasetParseJobHistoryService historyService) {
        this.historyService = historyService;
    }

    @Scheduled(
            initialDelay = INITIAL_DELAY_MILLISECONDS,
            fixedDelay = CLEANUP_INTERVAL_MILLISECONDS
    )
    public void cleanExpiredHistory() {
        try {
            int deleted = historyService.cleanExpiredHistory();
            if (deleted > 0) {
                log.info("已清理 {} 条到期的文件解析任务历史", deleted);
            }
        } catch (RuntimeException exception) {
            log.warn("文件解析任务历史清理失败，下轮将自动重试", exception);
        }
    }
}
