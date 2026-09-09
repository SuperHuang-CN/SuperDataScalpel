package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.LaunchArtifactUpload;
import cn.superhuang.data.scalpel.contract.execution.SparkJarTrialPreview;
import cn.superhuang.data.scalpel.contract.execution.SparkJarTrialPreviewSnapshot;
import cn.superhuang.data.scalpel.contract.execution.TaskExecutionLaunchDescriptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Publishes only the newest preview snapshot; failed uploads are retried on the next tick. */
final class TrialPreviewPublisher implements Consumer<SparkJarTrialPreview>, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TrialPreviewPublisher.class);
    private static final int MAXIMUM_BYTES = 4 * 1024 * 1024;

    private final TaskExecutionLaunchDescriptor launch;
    private final RunnerArtifactAccess artifacts;
    private final ObjectMapper objectMapper;
    private final LaunchArtifactUpload upload;
    private final AtomicLong revisions = new AtomicLong();
    private final AtomicReference<SparkJarTrialPreviewSnapshot> pending = new AtomicReference<>();
    private final ScheduledExecutorService scheduler;
    private final Object flushMonitor = new Object();
    private volatile long uploadedRevision;

    TrialPreviewPublisher(
            TaskExecutionLaunchDescriptor launch,
            RunnerArtifactAccess artifacts,
            ObjectMapper objectMapper
    ) {
        this.launch = launch;
        this.artifacts = artifacts;
        this.objectMapper = objectMapper;
        this.upload = launch.trialPreview();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "datascalpel-trial-preview");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::flushBestEffort, 3, 3, TimeUnit.SECONDS);
    }

    @Override
    public void accept(SparkJarTrialPreview preview) {
        long revision = revisions.incrementAndGet();
        pending.set(new SparkJarTrialPreviewSnapshot(
                SparkJarTrialPreviewSnapshot.CURRENT_SCHEMA_VERSION,
                launch.executionId(), launch.runId(), launch.attempt(), revision, Instant.now(), preview));
    }

    private void flushBestEffort() {
        synchronized (flushMonitor) {
            SparkJarTrialPreviewSnapshot snapshot = pending.get();
            if (snapshot == null || snapshot.revision() <= uploadedRevision) return;
            try {
                byte[] content = objectMapper.writeValueAsBytes(snapshot);
                if (content.length > MAXIMUM_BYTES) {
                    log.warn("event=TRIAL_PREVIEW_TOO_LARGE executionId={} runId={} attempt={} revision={}",
                            launch.executionId(), launch.runId(), launch.attempt(), snapshot.revision());
                    uploadedRevision = snapshot.revision();
                    return;
                }
                artifacts.upload(upload.putUrl(), content, "application/json", Duration.ofSeconds(5));
                uploadedRevision = snapshot.revision();
            } catch (Exception exception) {
                log.warn("event=TRIAL_PREVIEW_UPLOAD_FAILED executionId={} runId={} attempt={} revision={} message={}",
                        launch.executionId(), launch.runId(), launch.attempt(), snapshot.revision(),
                        RunnerLogSanitizer.sanitize(exception.getMessage()));
            }
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        flushBestEffort();
    }
}
