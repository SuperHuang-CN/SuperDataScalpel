package cn.superhuang.data.scalpel.business.filedataset.service.queue;

import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfigurationDefinition;
import cn.superhuang.data.scalpel.business.system.configuration.service.SystemConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Dynamically fills available worker slots from the shared durable queue. */
@Component
@ConditionalOnProperty(
        prefix = "data-scalpel.file-parsing",
        name = "background-worker-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class FileDatasetParseScheduler {

    static final long POLL_INTERVAL_MILLISECONDS = 1_000;
    private static final long WARNING_INTERVAL_NANOSECONDS = TimeUnit.SECONDS.toNanos(30);
    private static final Logger log = LoggerFactory.getLogger(FileDatasetParseScheduler.class);

    private final SystemConfigurationService configurationService;
    private final FileDatasetParseJobCoordinator coordinator;
    private final FileDatasetParseWorkerPool workerPool;
    private final AtomicLong lastWarningAt = new AtomicLong();

    public FileDatasetParseScheduler(
            SystemConfigurationService configurationService,
            FileDatasetParseJobCoordinator coordinator,
            FileDatasetParseWorkerPool workerPool
    ) {
        this.configurationService = configurationService;
        this.coordinator = coordinator;
        this.workerPool = workerPool;
    }

    @Scheduled(fixedDelay = POLL_INTERVAL_MILLISECONDS)
    public void schedule() {
        try {
            int recovered = coordinator.recoverExpiredLeases();
            if (recovered > 0) {
                log.info("已恢复 {} 个租约过期的文件解析任务", recovered);
            }
            boolean enabled = configurationService.requireBoolean(
                    SystemConfigurationDefinition.FILE_DATASET_PARSING_QUEUE_ENABLED
            );
            int concurrency = configurationService.requireInteger(
                    SystemConfigurationDefinition.FILE_DATASET_PARSING_WORKER_CONCURRENCY
            );
            int availableSlots = availableSlots(enabled, concurrency, workerPool.inFlightCount());
            for (int index = 0; index < availableSlots; index++) {
                if (!workerPool.submitOne()) {
                    break;
                }
            }
        } catch (RuntimeException exception) {
            logSchedulingFailure(exception);
        }
    }

    static int availableSlots(boolean enabled, int concurrency, int inFlight) {
        if (!enabled) {
            return 0;
        }
        if (concurrency < 1 || concurrency > FileDatasetParseWorkerPool.MAX_CONCURRENCY) {
            throw new IllegalArgumentException("文件解析并发数超出允许范围");
        }
        if (inFlight < 0) {
            throw new IllegalArgumentException("文件解析运行数不能小于零");
        }
        return Math.max(0, concurrency - inFlight);
    }

    private void logSchedulingFailure(RuntimeException exception) {
        long now = System.nanoTime();
        long previous = lastWarningAt.get();
        if ((previous == 0 || now - previous >= WARNING_INTERVAL_NANOSECONDS)
                && lastWarningAt.compareAndSet(previous, now)) {
            log.warn("文件解析队列本轮调度失败，稍后自动重试", exception);
        } else {
            log.debug("文件解析队列本轮调度失败", exception);
        }
    }
}
