package cn.superhuang.data.scalpel.business.task.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SparkJarLineageIngestionScheduler {
    private static final Logger log = LoggerFactory.getLogger(SparkJarLineageIngestionScheduler.class);
    private final SparkJarLineageIngestionService service;
    private final SparkJarLineageIngestionWorker worker;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon(true).name("spark-jar-lineage-worker").factory());
    private final AtomicBoolean running = new AtomicBoolean();
    private final String instanceId = UUID.randomUUID().toString();

    public SparkJarLineageIngestionScheduler(
            SparkJarLineageIngestionService service,
            SparkJarLineageIngestionWorker worker
    ) {
        this.service = service;
        this.worker = worker;
    }

    @Scheduled(fixedDelay = 1_000)
    public void schedule() {
        try {
            service.recoverExpired();
            if (running.compareAndSet(false, true)) {
                executor.execute(() -> {
                    try { while (worker.runOne(instanceId)) { } }
                    catch (RuntimeException exception) {
                        log.warn("Spark JAR 运行血缘 Worker 异常", exception);
                    } finally { running.set(false); }
                });
            }
        } catch (RuntimeException exception) {
            log.debug("Spark JAR 运行血缘队列本轮调度失败", exception);
        }
    }

    @PreDestroy
    void close() { executor.shutdown(); }
}
