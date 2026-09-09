package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.dialect.runtime.JdbcExecutionCancellation;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.operations.service.TaskRunAlertService;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.query.InsertSelectQuery;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlySelectQueryParser;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import cn.superhuang.data.scalpel.dialect.runtime.InsertSelectExecution;
import cn.superhuang.data.scalpel.dialect.runtime.JdbcInsertSelectExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;

/** Executes a queued run outside management-database transactions and persists its terminal state afterwards. */
@Component
public class TaskRunWorker {

    private final java.util.concurrent.ConcurrentMap<UUID, JdbcExecutionCancellation> cancellations = new java.util.concurrent.ConcurrentHashMap<>();

    public void cancel(UUID runId) {
        var cancellation = cancellations.get(runId);
        if (cancellation != null) cancellation.cancel();
    }

    private final TaskRunRepository runRepository;
    private final TaskRunAlertService runAlerts;
    private final DataSourceRepository dataSourceRepository;
    private final JdbcInsertSelectExecutor executor;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public TaskRunWorker(
            TaskRunRepository runRepository,
            TaskRunAlertService runAlerts,
            DataSourceRepository dataSourceRepository,
            JdbcInsertSelectExecutor executor,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.runRepository = runRepository;
        this.runAlerts = runAlerts;
        this.dataSourceRepository = dataSourceRepository;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void execute(UUID runId) {
        var cancellation = new JdbcExecutionCancellation();
        if (cancellations.putIfAbsent(runId, cancellation) != null) return;
        try { execute(runId, cancellation); }
        finally { cancellations.remove(runId, cancellation); }
    }

    private void execute(UUID runId, JdbcExecutionCancellation cancellation) {
        ExecutionContext context;
        try {
            context = requireTransactionResult(transactionTemplate.execute(status -> start(runId)));
        } catch (RuntimeException exception) {
            completeFailure(runId, "无法启动任务运行", exception.getClass().getSimpleName() + ": " + exception.getMessage(), false);
            return;
        }
        try {
            InsertSelectQuery query = ReadOnlySelectQueryParser.parse(context.snapshot().sql());
            InsertSelectExecution result = executor.execute(
                    context.source().getType().name(), context.source().getConnection().toJdbcConnectionConfig(),
                    new TableIdentifier(
                            context.snapshot().outputTarget().catalogName(), context.snapshot().outputTarget().schemaName(),
                            context.snapshot().outputTarget().tableName()
                    ),
                    context.snapshot().targetColumns(),
                    query,
                    context.snapshot().writeMode() == cn.superhuang.data.scalpel.business.task.domain.LocalSqlWriteMode.OVERWRITE,
                    Duration.ofSeconds(context.snapshot().timeoutSeconds()), cancellation
            );
            transactionTemplate.executeWithoutResult(status -> completeSuccess(runId, result.affectedRows()));
        } catch (DatabaseAccessException exception) {
            completeFailure(runId, exception.getMessage(), exception.code() + ": " + exception.getMessage(), "QUERY_TIMEOUT".equals(exception.code()));
        } catch (RuntimeException exception) {
            completeFailure(runId, "任务执行失败", exception.getClass().getSimpleName() + ": " + exception.getMessage(), false);
        }
    }

    private ExecutionContext start(UUID runId) {
        TaskRun run = requireRun(runId);
        if (run.getStatus() != TaskRunStatus.QUEUED) {
            throw new IllegalStateException("任务运行不处于待执行状态");
        }
        TaskRunDefinitionSnapshot snapshot = readSnapshot(run);
        DataSource source = dataSourceRepository.findById(snapshot.dataSourceId())
                .orElseThrow(() -> new IllegalStateException("运行快照引用的数据源不存在"));
        if (!source.isEnabled() || !source.getType().isJdbc() || source.getType() != snapshot.dataSourceType()) {
            throw new IllegalStateException("运行数据源已停用或类型已变化");
        }
        run.start();
        runRepository.saveAndFlush(run);
        return new ExecutionContext(source, snapshot);
    }

    private void completeSuccess(UUID runId, long affectedRows) {
        TaskRun run = requireRun(runId);
        if (run.getStatus() == TaskRunStatus.CANCEL_REQUESTED) {
            run.cancel("SQL 执行已取消", run.getStartedAt(), java.time.Instant.now());
            runAlerts.capture(run);
        } else if (run.getStatus() == TaskRunStatus.RUNNING) {
            run.succeed(affectedRows);
            runAlerts.capture(run);
            runRepository.saveAndFlush(run);
        }
    }

    private void completeFailure(UUID runId, String message, String detail, boolean timeout) {
        transactionTemplate.executeWithoutResult(status -> {
            TaskRun run = requireRun(runId);
            if (run.getStatus() == TaskRunStatus.CANCEL_REQUESTED) {
                run.cancel("SQL 执行已取消", run.getStartedAt(), java.time.Instant.now());
            } else if (timeout) {
                run.timeout(message, detail);
            } else {
                run.fail(message, detail);
            }
            runAlerts.capture(run);
            runRepository.saveAndFlush(run);
        });
    }

    private TaskRunDefinitionSnapshot readSnapshot(TaskRun run) {
        try {
            return objectMapper.readValue(run.getDefinitionSnapshot(), TaskRunDefinitionSnapshot.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("任务运行快照无效", exception);
        }
    }

    private TaskRun requireRun(UUID runId) {
        return runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new IllegalStateException("任务运行不存在"));
    }

    private static <T> T requireTransactionResult(T result) {
        if (result == null) {
            throw new IllegalStateException("事务未返回预期结果");
        }
        return result;
    }

    private record ExecutionContext(DataSource source, TaskRunDefinitionSnapshot snapshot) {
    }
}
