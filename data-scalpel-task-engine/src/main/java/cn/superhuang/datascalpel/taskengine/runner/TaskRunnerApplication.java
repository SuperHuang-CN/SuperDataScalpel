package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageEnvelope;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.RunnerFailedEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerResultAvailableEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerStartedEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerStreamingStoppedEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerUserObservabilityEvent;
import cn.superhuang.data.scalpel.contract.execution.SafeExecutionError;
import cn.superhuang.data.scalpel.contract.execution.TaskExecutionLaunchDescriptor;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionError;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionResult;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;

final class TaskRunnerApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskRunnerApplication.class);
    private final ObjectMapper objectMapper;
    private final RunnerLaunchLoader launchLoader;
    private final RunnerArtifactAccess artifactClient;
    private final RunnerEventPublisherFactory publisherFactory;
    private final RunnerTaskExecutor taskExecutor;
    private final QualityRunnerTaskExecutor qualityTaskExecutor;
    private final StreamingRunnerTaskExecutor streamingTaskExecutor;
    private final RunnerFailureClassifier failureClassifier = new RunnerFailureClassifier();

    TaskRunnerApplication(
            ObjectMapper objectMapper,
            RunnerArtifactAccess artifactClient,
            RunnerEventPublisherFactory publisherFactory,
            RunnerTaskExecutor taskExecutor,
            StreamingRunnerTaskExecutor streamingTaskExecutor,
            QualityRunnerTaskExecutor qualityTaskExecutor
    ) {
        this.objectMapper = objectMapper;
        this.launchLoader = new RunnerLaunchLoader(objectMapper);
        this.artifactClient = artifactClient;
        this.publisherFactory = publisherFactory;
        this.taskExecutor = taskExecutor;
        this.streamingTaskExecutor = streamingTaskExecutor;
        this.qualityTaskExecutor = qualityTaskExecutor;
    }

    TaskRunnerApplication(
            ObjectMapper objectMapper,
            RunnerArtifactAccess artifactClient,
            RunnerEventPublisherFactory publisherFactory,
            RunnerTaskExecutor taskExecutor,
            StreamingRunnerTaskExecutor streamingTaskExecutor
    ) {
        this(objectMapper, artifactClient, publisherFactory, taskExecutor, streamingTaskExecutor,
                new ModelQualityTaskExecutor()::execute);
    }

    TaskRunnerApplication(
            ObjectMapper objectMapper,
            RunnerArtifactAccess artifactClient,
            RunnerEventPublisherFactory publisherFactory,
            RunnerTaskExecutor taskExecutor
    ) {
        this(
                objectMapper,
                artifactClient,
                publisherFactory,
                taskExecutor,
                (manifest, sparkMode, launch, publisher) -> {
                    throw new RunnerExecutionException(
                            "STREAMING_RUNNER_UNAVAILABLE", "流式 Runner 未配置", null);
                },
                new ModelQualityTaskExecutor()::execute
        );
    }

    int run(Map<String, String> environment) {
        Instant runnerStartedAt = Instant.now();
        RunnerLaunchLoader.LoadedLaunch loaded;
        try {
            loaded = launchLoader.load(environment);
        } catch (Throwable throwable) {
            TaskExecutionError error = failureClassifier.classify(
                    throwable, RunnerFailureContext.task(
                            cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase.PREPARE));
            LOGGER.error("event=TASK_FAILED code={} category={} phase={} diagnosticId={}\n{}",
                    error.code(), error.category(), error.phase(), error.diagnosticId(),
                    RunnerLogSanitizer.stackTrace(throwable));
            return 2;
        }

        TaskExecutionLaunchDescriptor launch = loaded.descriptor();
        LOGGER.info("event=TASK_START executionId={} runId={} attempt={} startedAt={}",
                launch.executionId(), launch.runId(), launch.attempt(), runnerStartedAt);
        RunnerEventPublisher publisher = createPublisher(launch);
        TaskExecutionResult result;
        TaskExecutionManifest loadedManifest = null;
        try {
            byte[] manifestBytes = artifactClient.download(launch.manifest().getUrl(), launch.manifest().maxBytes());
            verifySha256(manifestBytes, launch.manifest().sha256());
            if (launch.deadlineAt() != null && Instant.now().isAfter(launch.deadlineAt())) {
                throw new RunnerExecutionException("EXECUTION_DEADLINE_EXCEEDED", "任务已超过执行截止时间", null);
            }
            TaskExecutionManifest manifest = objectMapper.readValue(manifestBytes, TaskExecutionManifest.class);
            loadedManifest = manifest;
            requireManifestIdentity(launch, manifest);
            if (manifest.executionTaskType() == ExecutionTaskType.SPARK_STREAMING_JAR) {
                if (launch.userJar() == null) {
                    throw new RunnerExecutionException("USER_JAR_DOWNLOAD_MISSING", "启动描述缺少用户 JAR", null);
                }
                Path userJar = loaded.workDirectory().resolve("user-job.jar");
                artifactClient.downloadToFile(launch.userJar().getUrl(), launch.userJar().sizeBytes(), userJar);
                verifySha256(userJar, launch.userJar().sha256());
                result = new SparkStreamingJarTaskExecutor(objectMapper)
                        .execute(manifest, launch.sparkMode(), launch, userJar, publisher);
                requireResultIdentity(launch, result);
            } else if (manifest.executionTaskType() == ExecutionTaskType.SPARK_JAR) {
                if (launch.userJar() == null) {
                    throw new RunnerExecutionException("USER_JAR_DOWNLOAD_MISSING", "启动描述缺少用户 JAR", null);
                }
                Path userJar = loaded.workDirectory().resolve("user-job.jar");
                artifactClient.downloadToFile(launch.userJar().getUrl(), launch.userJar().sizeBytes(), userJar);
                verifySha256(userJar, launch.userJar().sha256());
                result = new SparkJarTaskExecutor(objectMapper).execute(manifest, launch.sparkMode(), userJar, applicationId ->
                        publishBestEffort(publisher, new RunnerStartedEvent(
                                ExecutionMessageEnvelope.CURRENT_VERSION, UUID.randomUUID(),
                                ExecutionMessageType.RUNNER_STARTED, Instant.now(), launch.engineId(),
                                launch.executionId(), launch.runId(), launch.attempt(), applicationId)),
                        snapshot -> publishObservability(publisher, launch, snapshot));
                requireResultIdentity(launch, result);
            } else if (manifest.executionTaskType() == ExecutionTaskType.SPARK_MODEL_QUALITY) {
                result = qualityTaskExecutor.execute(manifest, launch.sparkMode(), applicationId -> {
                    publishBestEffort(publisher, new RunnerStartedEvent(
                            ExecutionMessageEnvelope.CURRENT_VERSION, UUID.randomUUID(),
                            ExecutionMessageType.RUNNER_STARTED, Instant.now(), launch.engineId(),
                            launch.executionId(), launch.runId(), launch.attempt(), applicationId));
                }, launch, loaded.workDirectory(), artifactClient);
                requireResultIdentity(launch, result);
            } else {
                if (manifest.task() != null
                        && manifest.task().executionMode() == CanvasExecutionMode.STREAMING) {
                    return runStreaming(launch, manifest, publisher);
                }
                if (launch.deadlineAt() == null) {
                    throw new RunnerExecutionException(
                            "INVALID_LAUNCH", "批处理任务缺少执行截止时间", null);
                }
                result = taskExecutor.execute(manifest, launch.sparkMode(), applicationId ->
                        publishBestEffort(publisher, new RunnerStartedEvent(
                                ExecutionMessageEnvelope.CURRENT_VERSION,
                                UUID.randomUUID(),
                                ExecutionMessageType.RUNNER_STARTED,
                                Instant.now(),
                                launch.engineId(),
                                launch.executionId(),
                                launch.runId(),
                                launch.attempt(),
                                applicationId
                        )));
                requireResultIdentity(launch, result);
            }
        } catch (Throwable throwable) {
            result = loadedManifest != null
                    && loadedManifest.executionTaskType() == ExecutionTaskType.SPARK_MODEL_QUALITY
                    ? ModelQualityTaskExecutor.failure(
                    launch.executionId(), launch.runId(), launch.attempt(), runnerStartedAt, throwable)
                    : loadedManifest != null && loadedManifest.executionTaskType() == ExecutionTaskType.SPARK_JAR
                    ? SparkJarTaskExecutor.failure(
                    launch.executionId(), launch.runId(), launch.attempt(), runnerStartedAt, throwable)
                    : loadedManifest != null
                    && loadedManifest.executionTaskType() == ExecutionTaskType.SPARK_STREAMING_JAR
                    ? SparkStreamingJarTaskExecutor.failure(
                    launch.executionId(), launch.runId(), launch.attempt(), runnerStartedAt, throwable)
                    : CanvasTaskExecutor.failure(
                    launch.executionId(), launch.runId(), launch.attempt(), runnerStartedAt, throwable);
            LOGGER.error(
                    "event=TASK_FAILURE_DETAIL executionId={} runId={} attempt={} code={} diagnosticId={}\n{}",
                    launch.executionId(), launch.runId(), launch.attempt(), result.error().code(),
                    result.error().diagnosticId(), RunnerLogSanitizer.stackTrace(throwable));
        }
        logTaskTerminal(launch, result);

        boolean resultUploaded = false;
        try {
            byte[] resultBytes = writeResult(loaded.workDirectory(), result);
            artifactClient.upload(launch.result().putUrl(), resultBytes, "application/json");
            resultUploaded = true;
            String digest = sha256(resultBytes);
            try {
                publisher.publish(new RunnerResultAvailableEvent(
                        ExecutionMessageEnvelope.CURRENT_VERSION,
                        UUID.randomUUID(),
                        ExecutionMessageType.RUNNER_RESULT_AVAILABLE,
                        Instant.now(),
                        launch.engineId(),
                        launch.executionId(),
                        launch.runId(),
                        launch.attempt(),
                        launch.result().objectKey(),
                        digest
                ));
                if (loadedManifest != null
                        && loadedManifest.executionTaskType() == ExecutionTaskType.SPARK_STREAMING_JAR) {
                    if (result.state() == TaskExecutionState.STOPPED) {
                        publisher.publish(new RunnerStreamingStoppedEvent(
                                ExecutionMessageEnvelope.CURRENT_VERSION, UUID.randomUUID(),
                                ExecutionMessageType.RUNNER_STREAMING_STOPPED, Instant.now(),
                                launch.engineId(), launch.executionId(), launch.runId(), launch.attempt(),
                                loadedManifest.execution().deploymentId(), Instant.now(),
                                "实时 JAR 任务已正常停止"));
                    } else {
                        publisher.publish(new RunnerFailedEvent(
                                ExecutionMessageEnvelope.CURRENT_VERSION, UUID.randomUUID(),
                                ExecutionMessageType.RUNNER_FAILED, Instant.now(), launch.engineId(),
                                launch.executionId(), launch.runId(), launch.attempt(), safeError(result.error())));
                    }
                }
            } catch (Exception eventFailure) {
                TaskExecutionError deliveryError = failureClassifier.classify(
                        eventFailure, RunnerFailureContext.task(
                                cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase.DELIVERY));
                logDeliveryFailure(launch, deliveryError, eventFailure);
                return 3;
            }
            return result.state() == TaskExecutionState.SUCCESS
                    || result.state() == TaskExecutionState.STOPPED ? 0 : 1;
        } catch (Throwable artifactOrEventFailure) {
            TaskExecutionError deliveryError = failureClassifier.classify(
                    artifactOrEventFailure, RunnerFailureContext.task(
                            cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase.DELIVERY));
            if (!resultUploaded) {
                publishBestEffort(publisher, new RunnerFailedEvent(
                        ExecutionMessageEnvelope.CURRENT_VERSION,
                        UUID.randomUUID(),
                        ExecutionMessageType.RUNNER_FAILED,
                        Instant.now(),
                        launch.engineId(),
                        launch.executionId(),
                        launch.runId(),
                        launch.attempt(),
                        safeError(deliveryError)
                ));
            }
            logDeliveryFailure(launch, deliveryError, artifactOrEventFailure);
            return 3;
        } finally {
            try {
                publisher.close();
            } catch (RuntimeException ignored) {
                // The result artifact is recoverable even if producer shutdown fails.
            }
        }
    }

    private int runStreaming(
            TaskExecutionLaunchDescriptor launch,
            TaskExecutionManifest manifest,
            RunnerEventPublisher publisher
    ) {
        try {
            streamingTaskExecutor.execute(
                    manifest, launch.sparkMode(), launch, publisher);
            return 0;
        } catch (Throwable throwable) {
            TaskExecutionError error = failureClassifier.classify(
                    throwable,
                    RunnerFailureContext.task(
                            cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase.PROCESS)
            );
            LOGGER.error(
                    "event=TASK_FAILED executionId={} runId={} attempt={} code={} category={} phase={} diagnosticId={}\n{}",
                    launch.executionId(), launch.runId(), launch.attempt(), error.code(),
                    error.category(), error.phase(), error.diagnosticId(),
                    RunnerLogSanitizer.stackTrace(throwable)
            );
            publishBestEffort(publisher, new RunnerFailedEvent(
                    ExecutionMessageEnvelope.CURRENT_VERSION,
                    UUID.randomUUID(),
                    ExecutionMessageType.RUNNER_FAILED,
                    Instant.now(),
                    launch.engineId(),
                    launch.executionId(),
                    launch.runId(),
                    launch.attempt(),
                    safeError(error)
            ));
            return 1;
        } finally {
            try {
                publisher.close();
            } catch (RuntimeException ignored) {
                // The Dispatcher reconciles the backend after Runner shutdown.
            }
        }
    }

    private RunnerEventPublisher createPublisher(TaskExecutionLaunchDescriptor launch) {
        try {
            return publisherFactory.create(launch);
        } catch (RuntimeException exception) {
            return new RunnerEventPublisher() {
                @Override public void publish(cn.superhuang.data.scalpel.contract.execution.RunnerExecutionEvent event) {
                    throw new IllegalStateException("Kafka Producer 不可用");
                }
                @Override public void close() { }
            };
        }
    }

    private static void publishObservability(
            RunnerEventPublisher publisher,
            TaskExecutionLaunchDescriptor launch,
            cn.superhuang.data.scalpel.contract.execution.UserJobObservabilitySnapshot snapshot
    ) {
        try {
            publisher.publish(new RunnerUserObservabilityEvent(
                    ExecutionMessageEnvelope.CURRENT_VERSION,
                    UUID.randomUUID(),
                    ExecutionMessageType.RUNNER_USER_OBSERVABILITY,
                    Instant.now(),
                    launch.engineId(),
                    launch.executionId(),
                    launch.runId(),
                    launch.attempt(),
                    snapshot));
        } catch (Exception exception) {
            throw new IllegalStateException("用户作业观测事件发送失败", exception);
        }
    }

    private static void publishBestEffort(
            RunnerEventPublisher publisher,
            cn.superhuang.data.scalpel.contract.execution.RunnerExecutionEvent event
    ) {
        try {
            publisher.publish(event);
        } catch (Exception ignored) {
            // Dispatcher reconciles uploaded result.json when a Runner signal is temporarily unavailable.
        }
    }

    private byte[] writeResult(Path workDirectory, TaskExecutionResult result) throws IOException {
        Files.createDirectories(workDirectory);
        byte[] content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
        Path temporary = workDirectory.resolve("result.json.tmp");
        Path target = workDirectory.resolve("result.json");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return content;
    }

    private static void requireManifestIdentity(
            TaskExecutionLaunchDescriptor launch,
            TaskExecutionManifest manifest
    ) {
        ManifestVersionSupport.requireSupported(manifest);
        if (manifest.execution() == null
                || !launch.executionId().equals(manifest.execution().executionId())
                || !launch.runId().equals(manifest.execution().runId())
                || launch.attempt() != manifest.execution().attempt()
                || !Objects.equals(launch.deadlineAt(), manifest.execution().deadlineAt())) {
            throw new RunnerExecutionException("MANIFEST_IDENTITY_MISMATCH", "manifest 执行标识不匹配", null);
        }
    }

    private static void requireResultIdentity(
            TaskExecutionLaunchDescriptor launch,
            TaskExecutionResult result
    ) {
        if (result == null || result.schemaVersion() == null
                || result.schemaVersion() != TaskExecutionResult.CURRENT_SCHEMA_VERSION
                || !launch.executionId().equals(result.executionId()) || !launch.runId().equals(result.runId())
                || result.attempt() == null || launch.attempt() != result.attempt()
                || result.state() == null || !result.state().terminal()) {
            throw new RunnerExecutionException("INVALID_RUNNER_RESULT", "Runner 返回了无效执行结果", null);
        }
    }

    private static void verifySha256(byte[] content, String expected) throws Exception {
        String actual = sha256(content);
        if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII))) {
            throw new RunnerExecutionException("MANIFEST_DIGEST_MISMATCH", "manifest SHA-256 校验失败", null);
        }
    }

    private static void verifySha256(Path content, String expected) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(content)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII))) {
            throw new RunnerExecutionException("USER_JAR_DIGEST_MISMATCH", "用户 JAR SHA-256 校验失败", null);
        }
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static SafeExecutionError safeError(TaskExecutionError error) {
        return new SafeExecutionError(
                error.code(), error.message(), error.category(), error.retryable(), error.nodeId(),
                error.nodeType(), error.nodeName(), error.phase(), error.sqlState(), error.diagnosticId());
    }

    private static void logTaskTerminal(TaskExecutionLaunchDescriptor launch, TaskExecutionResult result) {
        if (result.state() == TaskExecutionState.SUCCESS || result.state() == TaskExecutionState.STOPPED) {
            LOGGER.info(
                    "event=TASK_TERMINAL executionId={} runId={} attempt={} state={} durationMs={} affectedRows={}",
                    launch.executionId(), launch.runId(), launch.attempt(), result.state(),
                    result.durationMs(), result.affectedRows());
            return;
        }
        LOGGER.error(
                "event=TASK_FAILED executionId={} runId={} attempt={} state={} durationMs={} code={} category={} phase={} retryable={} diagnosticId={}",
                launch.executionId(), launch.runId(), launch.attempt(), result.state(), result.durationMs(),
                result.error().code(), result.error().category(), result.error().phase(),
                result.error().retryable(), result.error().diagnosticId());
    }

    private static void logDeliveryFailure(
            TaskExecutionLaunchDescriptor launch,
            TaskExecutionError error,
            Throwable throwable
    ) {
        LOGGER.error(
                "event=TASK_FAILED executionId={} runId={} attempt={} code={} category={} phase={} retryable={} diagnosticId={}\n{}",
                launch.executionId(), launch.runId(), launch.attempt(), error.code(), error.category(),
                error.phase(), error.retryable(), error.diagnosticId(), RunnerLogSanitizer.stackTrace(throwable));
    }
}
