package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.execution.RunnerSparkMode;
import cn.superhuang.data.scalpel.contract.execution.UserJobObservabilitySnapshot;
import cn.superhuang.datascalpel.sdk.SparkBatchJob;
import cn.superhuang.datascalpel.taskengine.contract.*;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import org.apache.spark.sql.SparkSession;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.ObjectMapper;

final class SparkJarTaskExecutor {
    private final RunnerFailureClassifier failureClassifier = new RunnerFailureClassifier();
    private final ObjectMapper objectMapper;

    SparkJarTaskExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    TaskExecutionResult execute(TaskExecutionManifest manifest, RunnerSparkMode sparkMode,
                                Path userJar, Consumer<String> sparkStarted,
                                Consumer<UserJobObservabilitySnapshot> observabilityPublisher,
                                Consumer<cn.superhuang.data.scalpel.contract.execution.SparkJarTrialPreview>
                                        trialPreviewPublisher) {
        Instant startedAt = Instant.now();
        SparkSession spark = null;
        SparkJarJobContextImpl context = null;
        try {
            validate(manifest, userJar);
            SparkSession.Builder builder = SedonaSparkSupport.builder()
                    .appName("DataScalpel Spark JAR " + manifest.execution().executionId())
                    .config("spark.ui.enabled", "false")
                    .config("spark.sql.shuffle.partitions", "4")
                    .config("spark.sql.caseSensitive", "true")
                    .config("spark.sql.ansi.enabled", "true")
                    .config("spark.sql.session.timeZone", "UTC")
                    .config("spark.speculation", "false");
            manifest.sparkJarJob().sparkConf().forEach(entry -> builder.config(entry.name(), entry.value()));
            if (sparkMode == RunnerSparkMode.LOCAL) builder.master("local[*]");
            spark = SedonaSparkSupport.initialize(builder.getOrCreate());
            spark.sparkContext().addJar(userJar.toUri().toString());
            sparkStarted.accept(spark.sparkContext().applicationId());
            context = new SparkJarJobContextImpl(
                    spark, manifest, objectMapper, observabilityPublisher, trialPreviewPublisher);
            context.observabilityRuntime().platformStatus("INITIALIZING", "正在初始化用户作业");
            context.observabilityRuntime().platformStatus("RUNNING", "正在执行用户作业");
            invoke(userJar, manifest.sparkJarJob().jobClass(), context);
            context.observabilityRuntime().platformStatus("FINALIZING", "正在完成用户作业");
            Instant endedAt = Instant.now();
            return new TaskExecutionResult(TaskExecutionResult.CURRENT_SCHEMA_VERSION,
                    manifest.execution().executionId(), manifest.execution().runId(), manifest.execution().attempt(),
                    TaskExecutionState.SUCCESS, startedAt, endedAt,
                    Duration.between(startedAt, endedAt).toMillis(), context.affectedRows(), List.of(),
                    ExecutionTaskType.SPARK_JAR, null,
                    context.observabilityRuntime().snapshot(), context.lineageEvidence(true),
                    context.trialPreview(), null);
        } catch (Throwable throwable) {
            Throwable actual = unwrap(throwable);
            TaskExecutionError error = failureClassifier.classify(actual,
                    RunnerFailureContext.task(ExecutionFailurePhase.PROCESS));
            Instant endedAt = Instant.now();
            return new TaskExecutionResult(TaskExecutionResult.CURRENT_SCHEMA_VERSION,
                    manifest.execution().executionId(), manifest.execution().runId(), manifest.execution().attempt(),
                    TaskExecutionState.FAILED, startedAt, endedAt,
                    Duration.between(startedAt, endedAt).toMillis(),
                    context == null ? null : context.affectedRows(), List.of(),
                    ExecutionTaskType.SPARK_JAR, null,
                    context == null ? null : context.observabilityRuntime().snapshot(),
                    context == null ? null : context.lineageEvidence(false),
                    context == null ? null : context.trialPreview(), error);
        } finally {
            if (context != null) context.observabilityRuntime().close();
            if (spark != null) spark.stop();
        }
    }

    static TaskExecutionResult failure(java.util.UUID executionId, java.util.UUID runId, int attempt,
                                       Instant startedAt, Throwable throwable) {
        TaskExecutionError error = new RunnerFailureClassifier().classify(throwable,
                RunnerFailureContext.task(ExecutionFailurePhase.PREPARE));
        Instant endedAt = Instant.now();
        return new TaskExecutionResult(TaskExecutionResult.CURRENT_SCHEMA_VERSION,
                executionId, runId, attempt, TaskExecutionState.FAILED, startedAt, endedAt,
                Duration.between(startedAt, endedAt).toMillis(), null, List.of(),
                ExecutionTaskType.SPARK_JAR, null, null, null, null, error);
    }

    private static void invoke(Path userJar, String className, SparkJarJobContextImpl context) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{userJar.toUri().toURL()},
                SparkBatchJob.class.getClassLoader())) {
            Class<?> jobClass;
            try { jobClass = Class.forName(className, true, loader); }
            catch (ClassNotFoundException exception) {
                throw new RunnerExecutionException("USER_JOB_CLASS_NOT_FOUND", "用户 Job Class 无法加载", null);
            } catch (LinkageError | SecurityException exception) {
                throw new RunnerExecutionException("USER_JOB_CLASS_LOAD_FAILED", "用户 Job Class 初始化或链接失败", null, exception);
            }
            if (!SparkBatchJob.class.isAssignableFrom(jobClass) || !Modifier.isPublic(jobClass.getModifiers())
                    || Modifier.isAbstract(jobClass.getModifiers()))
                throw new RunnerExecutionException("USER_JOB_CLASS_INVALID", "用户 Job Class 必须是 public 并实现 SparkBatchJob", null);
            var constructor = jobClass.getConstructor();
            if (!Modifier.isPublic(constructor.getModifiers()))
                throw new RunnerExecutionException("USER_JOB_CONSTRUCTOR_INVALID", "用户 Job Class 必须有 public 无参构造方法", null);
            SparkBatchJob job;
            try {
                job = (SparkBatchJob) constructor.newInstance();
            } catch (InvocationTargetException exception) {
                throw new RunnerExecutionException("USER_JOB_CONSTRUCTION_FAILED", "用户 Job Class 构造失败", null,
                        exception.getCause() == null ? exception : exception.getCause());
            } catch (ReflectiveOperationException | SecurityException exception) {
                throw new RunnerExecutionException("USER_JOB_CONSTRUCTION_FAILED", "用户 Job Class 构造失败", null, exception);
            }
            Thread thread = Thread.currentThread(); ClassLoader previous = thread.getContextClassLoader();
            thread.setContextClassLoader(loader);
            try { job.execute(context); }
            catch (Throwable throwable) {
                if (findKnownExecutionFailure(throwable)) throw throwable;
                throw new RunnerExecutionException("USER_JOB_EXECUTION_FAILED", "用户 Spark 作业执行失败", null, throwable);
            } finally { thread.setContextClassLoader(previous); }
        } catch (NoSuchMethodException exception) {
            throw new RunnerExecutionException("USER_JOB_CONSTRUCTOR_INVALID", "用户 Job Class 缺少 public 无参构造方法", null);
        }
    }

    private static boolean findKnownExecutionFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof java.sql.SQLException || current instanceof RunnerExecutionException
                    || current.getClass().getName().startsWith("org.apache.spark.")) return true;
        }
        return false;
    }

    private static Throwable unwrap(Throwable throwable) {
        return throwable instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : throwable;
    }

    private static void validate(TaskExecutionManifest manifest, Path userJar) {
        if (manifest == null || manifest.executionTaskType() != ExecutionTaskType.SPARK_JAR
                || manifest.sparkJarJob() == null || manifest.task() != null || manifest.modelQuality() != null
                || userJar == null || !java.nio.file.Files.isRegularFile(userJar))
            throw new RunnerExecutionException("INVALID_SPARK_JAR_MANIFEST", "Spark JAR Manifest 无效", null);
    }
}
