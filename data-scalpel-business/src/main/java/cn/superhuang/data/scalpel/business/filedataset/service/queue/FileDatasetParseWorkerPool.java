package cn.superhuang.data.scalpel.business.filedataset.service.queue;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded executor with no in-memory waiting queue; PostgreSQL remains the only parse queue. */
@Component
public class FileDatasetParseWorkerPool {

    public static final int MAX_CONCURRENCY = 16;

    private static final Logger log = LoggerFactory.getLogger(FileDatasetParseWorkerPool.class);

    private final FileDatasetParseWorker worker;
    private final ThreadPoolExecutor executor;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong workerSequence = new AtomicLong();
    private final String instanceId = UUID.randomUUID().toString();

    public FileDatasetParseWorkerPool(FileDatasetParseWorker worker) {
        this.worker = worker;
        this.executor = new ThreadPoolExecutor(
                0,
                MAX_CONCURRENCY,
                60,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                Thread.ofPlatform().daemon(true).name("file-dataset-parse-worker-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public int inFlightCount() {
        return inFlight.get();
    }

    public boolean submitOne() {
        String workerId = instanceId + ":" + workerSequence.incrementAndGet();
        inFlight.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    worker.runOne(workerId);
                } catch (RuntimeException exception) {
                    log.error("文件解析 Worker 未预期退出，workerId={}", workerId, exception);
                } finally {
                    inFlight.decrementAndGet();
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            inFlight.decrementAndGet();
            return false;
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.info("仍有文件解析 Worker 在关闭窗口后运行，交由租约恢复机制处理");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
