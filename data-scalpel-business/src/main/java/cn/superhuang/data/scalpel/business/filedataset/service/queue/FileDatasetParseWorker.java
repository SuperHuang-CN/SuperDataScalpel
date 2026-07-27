package cn.superhuang.data.scalpel.business.filedataset.service.queue;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobType;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetContentParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingException;
import cn.superhuang.data.scalpel.business.filedataset.service.prepare.FileDatasetPreparationService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Claims and executes one durable preparation or table parse job without a long database transaction. */
@Component
public class FileDatasetParseWorker {

    static final int SAMPLE_RECORD_LIMIT = 1_000;
    static final long HEARTBEAT_INTERVAL_SECONDS = 20;

    private static final Logger log = LoggerFactory.getLogger(FileDatasetParseWorker.class);

    private final FileDatasetParseJobCoordinator coordinator;
    private final FileDatasetContentParser contentParser;
    private final List<FileDatasetPreparationService> preparationServices;
    private final FileDatasetParseFailureClassifier failureClassifier;
    private final ScheduledThreadPoolExecutor heartbeatExecutor;

    public FileDatasetParseWorker(
            FileDatasetParseJobCoordinator coordinator,
            FileDatasetContentParser contentParser,
            List<FileDatasetPreparationService> preparationServices,
            FileDatasetParseFailureClassifier failureClassifier
    ) {
        this.coordinator = coordinator;
        this.contentParser = contentParser;
        this.preparationServices = List.copyOf(preparationServices);
        this.failureClassifier = failureClassifier;
        this.heartbeatExecutor = new ScheduledThreadPoolExecutor(
                2,
                Thread.ofPlatform().daemon(true).name("file-dataset-parse-heartbeat-", 0).factory()
        );
        this.heartbeatExecutor.setRemoveOnCancelPolicy(true);
    }

    public ExecutionOutcome runOne(String workerId) {
        Optional<FileDatasetParseJobCoordinator.ClaimedJob> claimed = coordinator.claimNext(workerId);
        if (claimed.isEmpty()) {
            return ExecutionOutcome.NO_JOB;
        }
        FileDatasetParseJobCoordinator.ClaimedJob job = claimed.orElseThrow();
        AtomicBoolean leaseLost = new AtomicBoolean(false);
        ScheduledFuture<?> heartbeat = startHeartbeat(job, leaseLost);
        return job.type() == FileDatasetParseJobType.FILE_PREPARATION
                ? runPreparation(job, heartbeat, leaseLost)
                : runTableParse(job, heartbeat, leaseLost);
    }

    private ExecutionOutcome runTableParse(
            FileDatasetParseJobCoordinator.ClaimedJob job,
            ScheduledFuture<?> heartbeat,
            AtomicBoolean leaseLost
    ) {
        FileDatasetParser.ParseResult result;
        try {
            result = contentParser.validate(job.input(), SAMPLE_RECORD_LIMIT);
            if (result.fields().isEmpty()) {
                throw new FileDatasetParsingException("未识别到可用字段");
            }
        } catch (Exception exception) {
            heartbeat.cancel(false);
            return recordFailure(job, exception);
        }
        heartbeat.cancel(false);
        if (leaseLost.get()) {
            log.warn("表解析完成但租约已丢失，丢弃结果，jobId={}, tableId={}", job.jobId(), job.tableId());
            return ExecutionOutcome.STALE;
        }
        try {
            boolean committed = coordinator.completeTableSuccess(job, result);
            if (!committed) {
                log.warn("表解析结果已过期，未提交，jobId={}, tableId={}", job.jobId(), job.tableId());
                return ExecutionOutcome.STALE;
            }
            return ExecutionOutcome.SUCCEEDED;
        } catch (FileDatasetParsingException exception) {
            return recordFailure(job, exception);
        } catch (RuntimeException exception) {
            log.error(
                    "提交表解析成功结果失败，将由租约恢复重试，jobId={}, datasetId={}, fileId={}, tableId={}",
                    job.jobId(), job.datasetId(), job.sourceFileId(), job.tableId(), exception
            );
            return ExecutionOutcome.PERSISTENCE_ERROR;
        }
    }

    private ExecutionOutcome runPreparation(
            FileDatasetParseJobCoordinator.ClaimedJob job,
            ScheduledFuture<?> heartbeat,
            AtomicBoolean leaseLost
    ) {
        FileDatasetPreparationService preparationService = requirePreparationService(job);
        FileDatasetPreparationService.FileDatasetPreparationResult result;
        try {
            result = preparationService.prepare(job.preparationInput());
        } catch (Exception exception) {
            heartbeat.cancel(false);
            return recordFailure(job, exception);
        }
        heartbeat.cancel(false);
        if (leaseLost.get()) {
            preparationService.discard(result.materializedPrefix());
            log.warn("文件准备完成但租约已丢失，丢弃物化前缀，jobId={}, fileId={}", job.jobId(), job.sourceFileId());
            return ExecutionOutcome.STALE;
        }
        try {
            boolean committed = coordinator.completePreparationSuccess(job, result);
            if (!committed) {
                preparationService.discard(result.materializedPrefix());
                log.warn("文件准备结果已过期，未发布，jobId={}, fileId={}", job.jobId(), job.sourceFileId());
                return ExecutionOutcome.STALE;
            }
            return ExecutionOutcome.SUCCEEDED;
        } catch (RuntimeException exception) {
            log.error(
                    "提交文件准备结果失败，将由租约恢复重试，jobId={}, datasetId={}, fileId={}",
                    job.jobId(), job.datasetId(), job.sourceFileId(), exception
            );
            return ExecutionOutcome.PERSISTENCE_ERROR;
        }
    }

    private FileDatasetPreparationService requirePreparationService(
            FileDatasetParseJobCoordinator.ClaimedJob job
    ) {
        return preparationServices.stream()
                .filter(service -> service.supports(job.preparationInput().format()))
                .findFirst()
                .orElseThrow(() -> new FileDatasetParsingException(
                        "暂不支持 " + job.preparationInput().format() + " 文件准备"
                ));
    }

    private ExecutionOutcome recordFailure(
            FileDatasetParseJobCoordinator.ClaimedJob job,
            Exception exception
    ) {
        FileDatasetParseFailureClassifier.Failure failure = failureClassifier.classify(exception);
        log.warn(
                "文件解析执行失败，type={}, jobId={}, datasetId={}, fileId={}, tableId={}, retryable={}",
                job.type(), job.jobId(), job.datasetId(), job.sourceFileId(), job.tableId(), failure.retryable(), exception
        );
        try {
            return switch (coordinator.completeFailure(job, failure)) {
                case REQUEUED -> ExecutionOutcome.REQUEUED;
                case FAILED -> ExecutionOutcome.FAILED;
                case STALE -> ExecutionOutcome.STALE;
            };
        } catch (RuntimeException persistenceException) {
            log.error(
                    "提交文件解析失败结果失败，将由租约恢复处理，jobId={}, tableId={}",
                    job.jobId(), job.tableId(), persistenceException
            );
            return ExecutionOutcome.PERSISTENCE_ERROR;
        }
    }

    private ScheduledFuture<?> startHeartbeat(
            FileDatasetParseJobCoordinator.ClaimedJob job,
            AtomicBoolean leaseLost
    ) {
        return heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (!coordinator.heartbeat(job.jobId(), job.workerId())) {
                    leaseLost.set(true);
                }
            } catch (RuntimeException exception) {
                log.warn("文件解析任务心跳失败，jobId={}, tableId={}", job.jobId(), job.tableId(), exception);
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdownHeartbeatExecutor() {
        heartbeatExecutor.shutdown();
    }

    public enum ExecutionOutcome {
        NO_JOB,
        SUCCEEDED,
        REQUEUED,
        FAILED,
        STALE,
        PERSISTENCE_ERROR
    }
}
