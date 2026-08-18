package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.*;
import cn.superhuang.datascalpel.sdk.*;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.StreamingQuery;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

final class SparkStreamingJarTaskExecutor {
    private static final Duration CONTROL_POLL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration PROGRESS_INTERVAL = Duration.ofSeconds(10);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final java.util.regex.Pattern LOGICAL_NAME =
            java.util.regex.Pattern.compile("[A-Za-z0-9._-]{1,100}");

    private final ObjectMapper objectMapper;

    SparkStreamingJarTaskExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void execute(
            TaskExecutionManifest manifest,
            RunnerSparkMode sparkMode,
            TaskExecutionLaunchDescriptor launch,
            Path userJar,
            RunnerEventPublisher publisher
    ) {
        validate(manifest, launch, userJar);
        SparkSession.Builder builder = SedonaSparkSupport.builder()
                .appName("DataScalpel Spark Streaming JAR " + manifest.execution().executionId())
                .config("spark.ui.enabled", "false")
                .config("spark.sql.shuffle.partitions", "4")
                .config("spark.sql.caseSensitive", "true")
                .config("spark.sql.ansi.enabled", "true")
                .config("spark.sql.session.timeZone", "UTC")
                .config("spark.speculation", "false")
                .config("spark.sql.streaming.stopTimeout", "60000")
                .config("spark.redaction.regex",
                        "(?i)secret|password|passwd|token|credential|api[-_.]?key|access[-_.]?key")
                .config("spark.sql.redaction.options.regex",
                        "(?i)secret|password|passwd|token|credential|api[-_.]?key|access[-_.]?key");
        manifest.streamingSparkJarJob().sparkConf()
                .forEach(entry -> builder.config(entry.name(), entry.value()));
        if (sparkMode == RunnerSparkMode.LOCAL) builder.master("local[*]");
        SparkSession spark = SedonaSparkSupport.initialize(builder.getOrCreate());
        spark.sparkContext().addJar(userJar.toUri().toString());

        AtomicReference<UserJobObservabilityRuntime> observabilityReference = new AtomicReference<>();
        ManagedQueries queries = new ManagedQueries(
                spark, manifest, launch.checkpointUriPrefix(), (logicalName, sinkType, queryName, count) -> {
                    UserJobObservabilityRuntime observability = observabilityReference.get();
                    if (observability == null) return;
                    observability.setPlatformGauge("datascalpel.streaming.registered_queries", count);
                    observability.platformInfo("streaming.query.registered", "StreamingQuery 注册成功", Map.of(
                            "logicalName", logicalName,
                            "sinkType", sinkType.name(),
                            "queryName", queryName));
                });
        SparkStreamingJarJobContextImpl context =
                new SparkStreamingJarJobContextImpl(
                        spark, manifest, queries, objectMapper,
                        snapshot -> publishObservability(publisher, launch, snapshot));
        observabilityReference.set(context.observabilityRuntime());
        context.observabilityRuntime().platformStatus("INITIALIZING", "正在初始化用户实时作业");
        SparkStreamingJob job = null;
        URLClassLoader loader = null;
        Throwable primary = null;
        boolean cleaned = false;
        boolean onStopInvoked = false;
        try {
            loader = new URLClassLoader(
                    new java.net.URL[]{userJar.toUri().toURL()},
                    SparkStreamingJob.class.getClassLoader());
            try (StreamingStopController stopController = new StreamingStopController(
                     launch.runnerControl(), objectMapper, launch.engineId(), launch.executionId(),
                     launch.runId(), launch.attempt(), manifest.execution().deploymentId())) {
                job = load(loader, manifest.streamingSparkJarJob().jobClass());
                context.observabilityRuntime().platformStatus("STARTING", "正在启动用户实时作业");
                StartOutcome outcome = startJob(job, context, loader, stopController,
                        manifest.streamingSparkJarJob().startupTimeoutSeconds());
                if (outcome == StartOutcome.STARTED) {
                    queries.validateRegisteredSet();
                    context.observabilityRuntime().platformStatus("RUNNING", "用户实时作业正在运行");
                    publish(publisher, new RunnerStreamingStartedEvent(
                            ExecutionMessageEnvelope.CURRENT_VERSION, UUID.randomUUID(),
                            ExecutionMessageType.RUNNER_STREAMING_STARTED, Instant.now(),
                            launch.engineId(), launch.executionId(), launch.runId(), launch.attempt(),
                            manifest.execution().deploymentId(), spark.sparkContext().applicationId(),
                            queries.descriptors()));
                    monitor(manifest, launch, publisher, stopController, queries);
                }
                context.observabilityRuntime().platformStatus("STOPPING", "正在停止用户实时作业");
                queries.stopAll();
                onStopInvoked = true;
                invokeOnStop(job, context, loader);
                cleaned = true;
            }
        } catch (Throwable throwable) {
            primary = unwrap(throwable);
            if (primary instanceof RunnerExecutionException runner) throw runner;
            throw new RunnerExecutionException(
                    "USER_STREAMING_JOB_EXECUTION_FAILED",
                    "用户 Spark Streaming 作业执行失败", null, primary);
        } finally {
            RuntimeException cleanupFailure = null;
            if (!cleaned) {
                Throwable cleanupProblem = null;
                try {
                    context.observabilityRuntime().platformStatus("STOPPING", "正在停止用户实时作业");
                } catch (Throwable cleanup) {
                    cleanupProblem = append(cleanupProblem, cleanup);
                }
                try {
                    queries.stopAll();
                } catch (Throwable cleanup) {
                    cleanupProblem = append(cleanupProblem, cleanup);
                }
                if (job != null && !onStopInvoked) {
                    onStopInvoked = true;
                    try {
                        invokeOnStop(job, context, loader);
                    } catch (Throwable cleanup) {
                        cleanupProblem = append(cleanupProblem, cleanup);
                    }
                }
                if (cleanupProblem != null) {
                    if (primary != null) primary.addSuppressed(cleanupProblem);
                    else cleanupFailure = cleanupProblem instanceof RuntimeException runtime ? runtime
                            : new RunnerExecutionException("STREAMING_JOB_ON_STOP_FAILED",
                            "用户 Streaming Job 清理失败", null, cleanupProblem);
                }
            }
            context.observabilityRuntime().close();
            try {
                spark.stop();
            } catch (RuntimeException cleanup) {
                if (primary != null) primary.addSuppressed(cleanup);
                else if (cleanupFailure == null) cleanupFailure = cleanup;
                else cleanupFailure.addSuppressed(cleanup);
            }
            if (loader != null) {
                try {
                    loader.close();
                } catch (java.io.IOException cleanup) {
                    if (primary != null) primary.addSuppressed(cleanup);
                    else if (cleanupFailure == null) cleanupFailure = new RunnerExecutionException(
                            "USER_JOB_CLASSLOADER_CLOSE_FAILED", "用户 JAR ClassLoader 关闭失败", null, cleanup);
                    else cleanupFailure.addSuppressed(cleanup);
                }
            }
            if (cleanupFailure != null) throw cleanupFailure;
        }
        publishStopped(manifest, launch, publisher);
    }

    private StartOutcome startJob(
            SparkStreamingJob job,
            SparkStreamingJarJobContextImpl context,
            ClassLoader loader,
            StreamingStopController stopController,
            int timeoutSeconds
    ) {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "datascalpel-streaming-job-start");
            thread.setDaemon(true);
            thread.setContextClassLoader(loader);
            return thread;
        });
        Future<?> future = executor.submit(() -> {
            try {
                job.start(context);
            } catch (Throwable throwable) {
                throw new CompletionException(throwable);
            }
        });
        Instant deadline = Instant.now().plusSeconds(timeoutSeconds);
        try {
            while (true) {
                try {
                    future.get(1, TimeUnit.SECONDS);
                    return StartOutcome.STARTED;
                } catch (TimeoutException ignored) {
                    if (stopController.poll(Duration.ZERO) != null) {
                        future.cancel(true);
                        return StartOutcome.STOPPED;
                    }
                    if (!Instant.now().isBefore(deadline)) {
                        future.cancel(true);
                        throw new RunnerExecutionException("STREAMING_JOB_START_TIMEOUT",
                                "用户 Streaming Job 在启动超时内未完成查询注册", null);
                    }
                } catch (ExecutionException exception) {
                    Throwable actual = unwrap(exception);
                    if (actual instanceof RunnerExecutionException runner) throw runner;
                    throw new RunnerExecutionException("USER_STREAMING_JOB_START_FAILED",
                            "用户 Streaming Job 启动失败", null, actual);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    future.cancel(true);
                    throw new RunnerExecutionException("USER_STREAMING_JOB_START_INTERRUPTED",
                            "用户 Streaming Job 启动被中断", null, exception);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void monitor(
            TaskExecutionManifest manifest,
            TaskExecutionLaunchDescriptor launch,
            RunnerEventPublisher publisher,
            StreamingStopController stopController,
            ManagedQueries queries
    ) {
        Instant lastPublished = Instant.EPOCH;
        long lastSignature = Long.MIN_VALUE;
        while (true) {
            if (stopController.poll(CONTROL_POLL_INTERVAL) != null) return;
            queries.validateRunningSet();
            List<StreamingQueryProgress> progress = queries.progress();
            long signature = progress.stream()
                    .mapToLong(value -> 31L * value.outputNodeId().hashCode() + value.batchId()).sum();
            Instant now = Instant.now();
            if ((signature != lastSignature && !lastPublished.plus(PROGRESS_INTERVAL).isAfter(now))
                    || !lastPublished.plus(HEARTBEAT_INTERVAL).isAfter(now)) {
                publish(publisher, new RunnerStreamingProgressEvent(
                        ExecutionMessageEnvelope.CURRENT_VERSION, UUID.randomUUID(),
                        ExecutionMessageType.RUNNER_STREAMING_PROGRESS, now,
                        launch.engineId(), launch.executionId(), launch.runId(), launch.attempt(),
                        manifest.execution().deploymentId(), progress, null));
                lastPublished = now;
                lastSignature = signature;
            }
        }
    }

    private static SparkStreamingJob load(URLClassLoader loader, String className) {
        try {
            Class<?> jobClass = Class.forName(className, true, loader);
            if (!SparkStreamingJob.class.isAssignableFrom(jobClass)
                    || !Modifier.isPublic(jobClass.getModifiers())
                    || Modifier.isAbstract(jobClass.getModifiers())) {
                throw new RunnerExecutionException("USER_STREAMING_JOB_CLASS_INVALID",
                        "用户 Job Class 必须是 public 并实现 SparkStreamingJob", null);
            }
            var constructor = jobClass.getConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                throw new RunnerExecutionException("USER_JOB_CONSTRUCTOR_INVALID",
                        "用户 Job Class 必须有 public 无参构造方法", null);
            }
            return (SparkStreamingJob) constructor.newInstance();
        } catch (RunnerExecutionException exception) {
            throw exception;
        } catch (ClassNotFoundException exception) {
            throw new RunnerExecutionException("USER_JOB_CLASS_NOT_FOUND",
                    "用户 Job Class 无法加载", null, exception);
        } catch (NoSuchMethodException exception) {
            throw new RunnerExecutionException("USER_JOB_CONSTRUCTOR_INVALID",
                    "用户 Job Class 缺少 public 无参构造方法", null, exception);
        } catch (InvocationTargetException exception) {
            throw new RunnerExecutionException("USER_JOB_CONSTRUCTION_FAILED",
                    "用户 Job Class 构造失败", null,
                    exception.getCause() == null ? exception : exception.getCause());
        } catch (ReflectiveOperationException | LinkageError | SecurityException exception) {
            throw new RunnerExecutionException("USER_JOB_CLASS_LOAD_FAILED",
                    "用户 Job Class 加载或构造失败", null, exception);
        }
    }

    private static void invokeOnStop(
            SparkStreamingJob job,
            SparkStreamingJarJobContextImpl context,
            ClassLoader loader
    ) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        if (loader != null) thread.setContextClassLoader(loader);
        try {
            job.onStop(context);
        } catch (Throwable throwable) {
            throw new RunnerExecutionException("STREAMING_JOB_ON_STOP_FAILED",
                    "用户 Streaming Job onStop 回调失败", null, throwable);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static void publishStopped(
            TaskExecutionManifest manifest,
            TaskExecutionLaunchDescriptor launch,
            RunnerEventPublisher publisher
    ) {
        publish(publisher, new RunnerStreamingStoppedEvent(
                ExecutionMessageEnvelope.CURRENT_VERSION, UUID.randomUUID(),
                ExecutionMessageType.RUNNER_STREAMING_STOPPED, Instant.now(),
                launch.engineId(), launch.executionId(), launch.runId(), launch.attempt(),
                manifest.execution().deploymentId(), Instant.now(), "实时 JAR 任务已正常停止"));
    }

    private static void publish(RunnerEventPublisher publisher, RunnerExecutionEvent event) {
        try {
            publisher.publish(event);
        } catch (Exception exception) {
            throw new RunnerExecutionException("STREAMING_EVENT_DELIVERY_FAILED",
                    "实时 Runner 事件发送失败", null, exception);
        }
    }

    private static void publishObservability(
            RunnerEventPublisher publisher,
            TaskExecutionLaunchDescriptor launch,
            UserJobObservabilitySnapshot snapshot
    ) {
        publish(publisher, new RunnerUserObservabilityEvent(
                ExecutionMessageEnvelope.CURRENT_VERSION,
                UUID.randomUUID(),
                ExecutionMessageType.RUNNER_USER_OBSERVABILITY,
                Instant.now(),
                launch.engineId(),
                launch.executionId(),
                launch.runId(),
                launch.attempt(),
                snapshot));
    }

    private static void validate(
            TaskExecutionManifest manifest,
            TaskExecutionLaunchDescriptor launch,
            Path userJar
    ) {
        ManifestVersionSupport.requireSupported(manifest);
        if (manifest.executionTaskType() != ExecutionTaskType.SPARK_STREAMING_JAR
                || manifest.streamingSparkJarJob() == null || manifest.sparkJarJob() != null
                || manifest.task() != null || manifest.streaming() != null || manifest.modelQuality() != null
                || manifest.execution() == null || manifest.execution().deploymentId() == null
                || manifest.execution().deadlineAt() != null
                || launch == null || launch.deadlineAt() != null
                || launch.userJar() == null || launch.checkpointUriPrefix() == null
                || launch.checkpointUriPrefix().isBlank() || launch.runnerControl() == null
                || userJar == null || !Files.isRegularFile(userJar)) {
            throw new RunnerExecutionException("INVALID_SPARK_STREAMING_JAR_MANIFEST",
                    "Spark Streaming JAR Manifest 无效", null);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof ExecutionException || current instanceof CompletionException
                || current instanceof InvocationTargetException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static Throwable append(Throwable current, Throwable next) {
        if (current == null) return next;
        current.addSuppressed(next);
        return current;
    }

    private enum StartOutcome { STARTED, STOPPED }

    private static final class ManagedQueries implements StreamingQueries {
        private final SparkSession spark;
        private final UUID deploymentId;
        private final String logicalCheckpointPrefix;
        private final String physicalCheckpointPrefix;
        private final QueryObserver observer;
        private final LinkedHashMap<String, RegisteredQuery> registered = new LinkedHashMap<>();

        private ManagedQueries(
                SparkSession spark,
                TaskExecutionManifest manifest,
                String physicalCheckpointPrefix,
                QueryObserver observer
        ) {
            this.spark = spark;
            this.deploymentId = manifest.execution().deploymentId();
            this.logicalCheckpointPrefix = trim(manifest.streamingSparkJarJob().checkpointKeyPrefix());
            this.physicalCheckpointPrefix = trim(physicalCheckpointPrefix);
            this.observer = observer;
        }

        @Override
        public synchronized StreamingQuery start(
                String logicalName,
                StreamingSinkType sinkType,
                StreamingQueryStarter starter
        ) throws Exception {
            if (logicalName == null || !LOGICAL_NAME.matcher(logicalName).matches()) {
                throw new RunnerExecutionException("STREAMING_QUERY_NAME_INVALID",
                        "实时查询逻辑名称只允许 1 到 100 位字母、数字、点、下划线和连字符", null);
            }
            if (registered.containsKey(logicalName)) {
                throw new RunnerExecutionException("STREAMING_QUERY_NAME_DUPLICATE",
                        "实时查询逻辑名称不能重复：" + logicalName, null);
            }
            Objects.requireNonNull(sinkType, "sinkType");
            Objects.requireNonNull(starter, "starter");
            UUID queryId = UUID.nameUUIDFromBytes(
                    (deploymentId + ":" + logicalName).getBytes(StandardCharsets.UTF_8));
            String queryName = "datascalpel-" + deploymentId + "-" + queryId;
            String logicalCheckpoint = logicalCheckpointPrefix + "/queries/" + logicalName;
            String physicalCheckpoint = physicalCheckpointPrefix + "/queries/" + logicalName;
            StreamingQuery query = starter.start(new StreamingQuerySpec(queryName, physicalCheckpoint));
            if (query == null || !query.isActive() || !queryName.equals(query.name())) {
                if (query != null && query.isActive()) query.stop();
                throw new RunnerExecutionException("STREAMING_QUERY_REGISTRATION_INVALID",
                        "注册查询必须处于 Active 状态并使用平台提供的 Query Name", null);
            }
            registered.put(logicalName, new RegisteredQuery(
                    queryId, logicalName, sinkType, logicalCheckpoint, query));
            observer.registered(logicalName, sinkType, queryName, registered.size());
            return query;
        }

        synchronized List<StreamingQueryDescriptor> descriptors() {
            return registered.values().stream().map(value -> new StreamingQueryDescriptor(
                    value.queryId(), value.logicalName(),
                    StreamingQuerySinkType.valueOf(value.sinkType().name()),
                    value.logicalCheckpoint())).toList();
        }

        synchronized void validateRegisteredSet() {
            if (registered.isEmpty()) {
                throw new RunnerExecutionException("STREAMING_QUERY_REQUIRED",
                        "用户 Streaming Job 至少必须注册一个查询", null);
            }
            validateRunningSet();
        }

        synchronized void validateRunningSet() {
            for (RegisteredQuery value : registered.values()) {
                if (value.query().exception().isDefined()) {
                    Throwable failure = value.query().exception().get();
                    stopAll();
                    throw new RunnerExecutionException("STREAMING_QUERY_FAILED",
                            "实时查询失败：" + value.logicalName(), value.queryId().toString(), failure);
                }
                if (!value.query().isActive()) {
                    stopAll();
                    throw new RunnerExecutionException("STREAMING_QUERY_STOPPED_UNEXPECTEDLY",
                            "实时查询意外停止：" + value.logicalName(), value.queryId().toString());
                }
            }
            Set<UUID> expected = registered.values().stream()
                    .map(value -> value.query().id()).collect(java.util.stream.Collectors.toSet());
            Set<UUID> active = Arrays.stream(spark.streams().active())
                    .map(StreamingQuery::id).collect(java.util.stream.Collectors.toSet());
            if (!active.equals(expected)) {
                stopAll();
                throw new RunnerExecutionException("UNREGISTERED_STREAMING_QUERY",
                        "检测到未通过 SDK 注册或已经消失的 StreamingQuery", null);
            }
        }

        synchronized List<StreamingQueryProgress> progress() {
            return registered.values().stream().map(value -> {
                org.apache.spark.sql.streaming.StreamingQueryProgress progress = value.query().lastProgress();
                if (progress == null) {
                    return new StreamingQueryProgress(
                            value.queryId().toString(), -1, 0, 0, 0, 0, Instant.now());
                }
                return new StreamingQueryProgress(
                        value.queryId().toString(), progress.batchId(), progress.numInputRows(),
                        Math.max(0D, progress.inputRowsPerSecond()),
                        Math.max(0D, progress.processedRowsPerSecond()),
                        Math.max(0L, progress.batchDuration()), parseTimestamp(progress.timestamp()));
            }).toList();
        }

        synchronized void stopAll() {
            RuntimeException first = null;
            for (RegisteredQuery value : registered.values()) {
                if (!value.query().isActive()) continue;
                try {
                    value.query().stop();
                } catch (TimeoutException exception) {
                    RuntimeException failure = new RunnerExecutionException("STREAMING_STOP_TIMEOUT",
                            "实时查询停止超时：" + value.logicalName(), value.queryId().toString(), exception);
                    if (first == null) first = failure; else first.addSuppressed(failure);
                }
            }
            if (first != null) throw first;
        }

        private static String trim(String value) {
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        }

        private static Instant parseTimestamp(String timestamp) {
            try { return Instant.parse(timestamp); }
            catch (DateTimeParseException exception) { return Instant.now(); }
        }
    }

    @FunctionalInterface
    private interface QueryObserver {
        void registered(String logicalName, StreamingSinkType sinkType, String queryName, int count);
    }

    private record RegisteredQuery(
            UUID queryId,
            String logicalName,
            StreamingSinkType sinkType,
            String logicalCheckpoint,
            StreamingQuery query
    ) {
    }
}
