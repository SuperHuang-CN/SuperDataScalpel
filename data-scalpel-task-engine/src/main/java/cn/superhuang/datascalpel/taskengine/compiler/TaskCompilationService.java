package cn.superhuang.datascalpel.taskengine.compiler;

import cn.superhuang.datascalpel.taskengine.compiler.canvas.CanvasCompilation;
import cn.superhuang.datascalpel.taskengine.compiler.canvas.CanvasTaskCompiler;
import cn.superhuang.datascalpel.taskengine.config.EngineConfiguration;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationRequest;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationResponse;
import cn.superhuang.data.scalpel.contract.task.TaskType;
import cn.superhuang.datascalpel.taskengine.http.TaskEngineException;
import cn.superhuang.datascalpel.taskengine.spark.SparkCompilationScope;
import cn.superhuang.datascalpel.taskengine.spark.SparkRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class TaskCompilationService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TaskCompilationService.class);

    private final SparkRuntime sparkRuntime;
    private final CanvasTaskCompiler canvasCompiler;
    private final Semaphore permits;
    private final Duration acquireTimeout;
    private final Duration compileTimeout;
    private final ExecutorService compilerExecutor;
    private final Map<UUID, ActiveCompilation> activeCompilations = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public TaskCompilationService(EngineConfiguration configuration, SparkRuntime sparkRuntime) {
        this.sparkRuntime = sparkRuntime;
        this.canvasCompiler = new CanvasTaskCompiler();
        this.permits = new Semaphore(configuration.maxCompileConcurrency(), true);
        this.acquireTimeout = configuration.acquireTimeout();
        this.compileTimeout = configuration.compileTimeout();
        this.compilerExecutor = Executors.newFixedThreadPool(
                configuration.maxCompileConcurrency(), namedThreads("task-compiler-"));
    }

    public TaskCompilationResponse compile(TaskCompilationRequest request) {
        validateRequest(request);
        ensureOpen();
        UUID requestId = request.requestId();
        ActiveCompilation active = new ActiveCompilation();
        if (activeCompilations.putIfAbsent(requestId, active) != null) {
            throw problem(409, "DUPLICATE_REQUEST_ID", "请求冲突", "相同 requestId 正在编译");
        }

        boolean acquired = false;
        long startedNanos = System.nanoTime();
        try {
            acquired = permits.tryAcquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw problem(429, "COMPILATION_BUSY", "编译资源繁忙", "任务编译资源繁忙，请稍后重试");
            }
            if (active.cancelled.get()) throw cancelled();

            MetadataIndex metadataIndex = MetadataIndex.create(request.metadataSnapshot());
            Future<TaskCompilationResponse> future = compilerExecutor.submit(() -> compileCanvas(
                    request, metadataIndex, active, startedNanos));
            active.future.set(future);
            try {
                return future.get(compileTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (CancellationException exception) {
                throw cancelled();
            } catch (TimeoutException exception) {
                active.cancelled.set(true);
                sparkRuntime.cancelCompilation(requestId);
                future.cancel(true);
                throw problem(504, "COMPILATION_TIMEOUT", "任务编译超时", "任务编译超过允许时长");
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof CompilationCancelledException) throw cancelled();
                if (cause instanceof TaskEngineException taskEngineException) throw taskEngineException;
                log.error("Unexpected task compilation failure for request {}", requestId, cause);
                throw problem(500, "INTERNAL_ERROR", "任务引擎内部错误", "任务编译失败");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            active.cancelled.set(true);
            sparkRuntime.cancelCompilation(requestId);
            throw cancelled();
        } finally {
            if (acquired) permits.release();
            activeCompilations.remove(requestId, active);
        }
    }

    public boolean cancel(UUID requestId) {
        ActiveCompilation active = activeCompilations.get(requestId);
        if (active == null) return false;
        active.cancelled.set(true);
        sparkRuntime.cancelCompilation(requestId);
        Future<?> future = active.future.get();
        if (future != null) future.cancel(true);
        return true;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        activeCompilations.forEach((requestId, active) -> {
            active.cancelled.set(true);
            sparkRuntime.cancelCompilation(requestId);
            Future<?> future = active.future.get();
            if (future != null) future.cancel(true);
        });
        compilerExecutor.shutdownNow();
        try {
            if (!compilerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Task compiler executor did not terminate within 10 seconds");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private TaskCompilationResponse compileCanvas(
            TaskCompilationRequest request,
            MetadataIndex metadataIndex,
            ActiveCompilation active,
            long startedNanos
    ) {
        try (SparkCompilationScope scope = sparkRuntime.openCompilation(request.requestId())) {
            if (active.cancelled.get()) throw new CompilationCancelledException();
            CanvasCompilation compilation = canvasCompiler.compile(
                    request.task().definition(), request.task().executionMode(),
                    metadataIndex, scope.session(), active.cancelled);
            return new TaskCompilationResponse(
                    request.requestId(),
                    TaskType.CANVAS,
                    compilation.valid(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos),
                    sparkRuntime.applicationId(),
                    compilation.canvasIssues(),
                    compilation.nodeResults(),
                    compilation.lineage()
            );
        }
    }

    private static void validateRequest(TaskCompilationRequest request) {
        if (request == null) throw badRequest("请求体不能为空");
        if (request.requestId() == null) throw badRequest("requestId 不能为空");
        if (request.task() == null || request.task().type() == null) throw badRequest("task.type 不能为空");
        if (request.task().type() != TaskType.CANVAS) {
            throw problem(400, "UNKNOWN_TASK_TYPE", "任务类型不受支持", "第一阶段只支持 CANVAS 任务");
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw problem(503, "ENGINE_NOT_READY", "任务引擎不可用", "任务引擎正在关闭");
        }
    }

    private static TaskEngineException badRequest(String detail) {
        return problem(400, "INVALID_REQUEST", "请求无效", detail);
    }

    private static TaskEngineException cancelled() {
        return problem(409, "COMPILATION_CANCELLED", "任务编译已取消", "任务编译已取消");
    }

    private static TaskEngineException problem(int status, String code, String title, String detail) {
        return new TaskEngineException(status, code, title, detail);
    }

    private static ThreadFactory namedThreads(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
    }

    private static final class ActiveCompilation {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicReference<Future<?>> future = new AtomicReference<>();
    }
}
