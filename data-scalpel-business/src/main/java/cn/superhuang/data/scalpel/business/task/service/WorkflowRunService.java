package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.business.task.repository.*;
import cn.superhuang.data.scalpel.business.task.web.response.*;
import cn.superhuang.data.scalpel.business.task.execution.service.TaskExecutionOutboxService;
import cn.superhuang.data.scalpel.business.operations.service.TaskRunAlertService;
import cn.superhuang.data.scalpel.contract.execution.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Business orchestration state; children remain ordinary TaskRuns with their own execution snapshots. */
@Service
public class WorkflowRunService {
    public static final Set<TaskRunStatus> ACTIVE = Set.of(TaskRunStatus.QUEUED, TaskRunStatus.RUNNING,
            TaskRunStatus.CANCEL_REQUESTED, TaskRunStatus.STOP_REQUESTED);
    private final TaskRunRepository runs;
    private final DataTaskRepository tasks;
    private final WorkflowTaskDefinitionRepository definitions;
    private final WorkflowTaskDefinitionService definitionService;
    private final TaskExecutionOutboxService outbox;
    private final TaskRunWorker worker;
    private final TaskRunAlertService alerts;
    private volatile boolean ready;

    public WorkflowRunService(TaskRunRepository runs, DataTaskRepository tasks,
            WorkflowTaskDefinitionRepository definitions, WorkflowTaskDefinitionService definitionService,
            TaskExecutionOutboxService outbox, TaskRunWorker worker, TaskRunAlertService alerts) {
        this.runs = runs; this.tasks = tasks; this.definitions = definitions;
        this.definitionService = definitionService; this.outbox = outbox;
        this.worker = worker; this.alerts = alerts;
    }
    public boolean isReady() { return ready; }
    public void ready() { ready = true; }
    private void requireReady() {
        if (!ready) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务启动清理尚未完成");
    }

    @Transactional
    public TaskRunResponse queue(UUID taskId) {
        requireReady();
        var task = tasks.findByIdForUpdate(taskId).orElseThrow(() -> missing("任务不存在"));
        return TaskRunResponse.from(queue(task, null, null));
    }
    @Transactional
    public void queueScheduled(DataTask task, TaskSchedule schedule, Instant fireAt) {
        requireReady();
        if (runs.findByScheduleIdAndScheduledFireAt(schedule.getId(), fireAt).isPresent()) return;
        queue(task, schedule, fireAt);
    }
    private TaskRun queue(DataTask task, TaskSchedule schedule, Instant fireAt) {
        if (task.getType() != TaskType.WORKFLOW || task.getStatus() != TaskStatus.PUBLISHED)
            throw conflict("只有已发布工作流可以运行");
        var definition = definitions.findByTaskId(task.getId()).orElseThrow(() -> conflict("工作流定义不存在"));
        boolean overlap = runs.existsByTaskIdAndStatusIn(task.getId(), ACTIVE);
        if (schedule == null && overlap) throw conflict("当前工作流已有活动运行");
        boolean skipped = schedule != null && schedule.getOverlapPolicy() == TaskOverlapPolicy.FORBID && overlap;
        return runs.saveAndFlush(TaskRun.queueWorkflow(task.getId(), definition.getVersion(), definition.getDefinitionJson(),
                schedule == null ? null : schedule.getId(), fireAt, skipped));
    }

    @Transactional(readOnly = true)
    public List<UUID> candidates() {
        if (!ready) return List.of();
        return runs.findAllByTaskTypeAndStatusIn(TaskType.WORKFLOW, ACTIVE).stream().map(TaskRun::getId).toList();
    }

    @Transactional
    public List<WorkflowDefinition.Node> advance(UUID runId) {
        var parent = requireParent(runId, true);
        if (!ACTIVE.contains(parent.getStatus())) return List.of();
        var children = children(parent.getId());
        if (parent.getStatus() == TaskRunStatus.CANCEL_REQUESTED) {
            boolean unconfirmed = children.values().stream().anyMatch(this::terminationUnconfirmed);
            if (unconfirmed) fail(parent, null, "WORKFLOW_STOP_FAILED", "子任务停止结果无法确认，请检查执行详情");
            else if (children.values().stream().noneMatch(child -> ACTIVE.contains(child.getStatus()))) {
                parent.cancel("工作流已取消", parent.getStartedAt(), Instant.now());
                alerts.capture(parent);
            }
            return List.of();
        }
        var failed = children.values().stream().filter(child -> !ACTIVE.contains(child.getStatus())
                && child.getStatus() != TaskRunStatus.SUCCESS).findFirst();
        if (failed.isPresent()) {
            fail(parent, failed.get().getWorkflowNodeId(), "WORKFLOW_CHILD_FAILED", "子任务未成功，工作流已停止");
            return List.of();
        }
        if (parent.getStatus() == TaskRunStatus.QUEUED) parent.start();
        var definition = definitionService.read(parent.getDefinitionSnapshot());
        if (children.size() == definition.nodes().size() && !children.isEmpty()
                && children.values().stream().allMatch(child -> child.getStatus() == TaskRunStatus.SUCCESS)) {
            parent.succeedWorkflow(); alerts.capture(parent); return List.of();
        }
        long available = definition.maxParallelism() - children.values().stream().filter(child -> ACTIVE.contains(child.getStatus())).count();
        if (available <= 0) return List.of();
        return definition.nodes().stream().filter(node -> !children.containsKey(node.id()))
                .filter(node -> dependenciesSucceeded(definition, node.id(), children)).limit(available).toList();
    }

    /** Called inside the child creation/outbox transaction, before taking the child task lock. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockForSubmission(UUID parentId, String nodeId, UUID taskId) {
        var parent = requireParent(parentId, true);
        if (parent.getStatus() != TaskRunStatus.RUNNING) throw conflict("工作流已停止派发");
        var definition = definitionService.read(parent.getDefinitionSnapshot());
        boolean matches = definition.nodes().stream().anyMatch(node -> node.id().equals(nodeId)
                && UUID.fromString(node.taskId()).equals(taskId));
        var children = children(parentId);
        if (!matches || children.containsKey(nodeId) || !dependenciesSucceeded(definition, nodeId, children))
            throw conflict("工作流节点不可重复提交或依赖尚未完成");
        if (children.values().stream().anyMatch(child -> !ACTIVE.contains(child.getStatus()) && child.getStatus() != TaskRunStatus.SUCCESS))
            throw conflict("已有子任务未成功，工作流停止派发");
        if (children.values().stream().filter(child -> ACTIVE.contains(child.getStatus())).count() >= definition.maxParallelism())
            throw conflict("工作流并行槽位已满");
    }

    @Transactional
    public void submissionFailed(UUID parentId, String nodeId, RuntimeException exception) {
        var parent = requireParent(parentId, true);
        if (parent.getStatus() != TaskRunStatus.RUNNING && parent.getStatus() != TaskRunStatus.QUEUED) return;
        var failed = children(parentId).values().stream().filter(child -> !ACTIVE.contains(child.getStatus())
                && child.getStatus() != TaskRunStatus.SUCCESS).findFirst();
        if (failed.isPresent()) {
            fail(parent, failed.get().getWorkflowNodeId(), "WORKFLOW_CHILD_FAILED", "子任务未成功，工作流已停止");
            return;
        }
        String reason = exception instanceof ResponseStatusException status && status.getReason() != null
                ? status.getReason() : "请检查任务发布状态、定义、并发实例和执行资源";
        fail(parent, nodeId, "WORKFLOW_SUBMISSION_FAILED", "子任务启动失败：" + reason.substring(0, Math.min(850, reason.length())));
    }

    @Transactional
    public void advancementFailed(UUID runId) {
        var parent = requireParent(runId, true);
        fail(parent, null, "WORKFLOW_ADVANCEMENT_FAILED", "工作流推进失败，请检查运行诊断日志");
    }

    @Transactional
    public TaskRunResponse cancel(UUID runId) {
        var parent = requireParent(runId, true);
        if (!ACTIVE.contains(parent.getStatus())) throw conflict("当前工作流已经结束");
        if (parent.getStatus() != TaskRunStatus.CANCEL_REQUESTED) {
            parent.requestCancel(); stopChildren(parent.getId());
        }
        return TaskRunResponse.from(parent);
    }

    @Transactional
    public TaskRunResponse cancelLocal(UUID runId) {
        var run = runs.findByIdForUpdate(runId).orElseThrow(() -> missing("运行不存在"));
        if (run.getTaskType() != TaskType.LOCAL_SQL) throw conflict("当前运行不是 Local SQL 任务");
        if (!ACTIVE.contains(run.getStatus())) throw conflict("当前任务已经结束");
        stopChild(run);
        return TaskRunResponse.from(run);
    }

    private void fail(TaskRun parent, String nodeId, String code, String message) {
        if (!ACTIVE.contains(parent.getStatus())) return;
        parent.fail(new SafeExecutionError(code, message, ExecutionErrorCategory.INTERNAL, false,
                nodeId, nodeId == null ? null : "WORKFLOW_TASK", nodeId,
                ExecutionFailurePhase.DISPATCH, null, UUID.randomUUID()));
        alerts.capture(parent);
        stopChildren(parent.getId());
    }
    private void stopChildren(UUID parentId) {
        for (var child : runs.findAllByParentRunIdOrderByQueuedAtAsc(parentId)) {
            var locked = runs.findByIdForUpdate(child.getId()).orElseThrow();
            if (ACTIVE.contains(locked.getStatus())) stopChild(locked);
        }
    }
    private void stopChild(TaskRun child) {
        if (child.getTaskType() == TaskType.LOCAL_SQL) {
            if (child.getStatus() == TaskRunStatus.QUEUED) {
                child.cancel("SQL 任务在执行前已取消", null, Instant.now());
                alerts.capture(child);
            } else {
                child.requestCancel();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { worker.cancel(child.getId()); }
                });
            }
            return;
        }
        if (child.getStatus() == TaskRunStatus.CANCEL_REQUESTED) {
            // An earlier ordinary cancel may need the workflow's immediate hard stop too.
            if (outbox.hasForceTerminate(child.getExecutionRunId(), child.getExternalExecutionId())) return;
        } else child.requestCancel();
        outbox.enqueue(child.getCommandTopicSnapshot(), new ForceTerminateExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.FORCE_TERMINATE_EXECUTION, Instant.now(),
                child.getComputeEngineId(), child.getExternalExecutionId(), child.getExecutionRunId(), child.getAttempt(),
                "工作流停止子任务"));
    }

    @Transactional
    public void recoverInterrupted() {
        for (var candidate : runs.findAllByTaskTypeAndStatusIn(TaskType.WORKFLOW, ACTIVE)) {
            var parent = requireParent(candidate.getId(), true);
            if (ACTIVE.contains(parent.getStatus())) fail(parent, null, "APPLICATION_RESTARTED", "应用重启导致工作流中断");
        }
    }
    @Transactional(readOnly = true)
    public WorkflowRunResponse get(UUID runId) {
        var parent = requireParent(runId, false);
        var definition = definitionService.read(parent.getDefinitionSnapshot());
        var children = children(runId);
        var names = new HashMap<UUID, String>();
        var ids = definition.nodes().stream().map(n -> UUID.fromString(n.taskId())).distinct().toList();
        tasks.findAllById(ids).forEach(task -> names.put(task.getId(), task.getName()));
        var nodes = definition.nodes().stream().map(node -> {
            var child = children.get(node.id());
            String state = child == null ? projectedStatus(parent, node.id()) : child.getStatus().name();
            String message = child == null ? (Objects.equals(parent.getErrorNodeId(), node.id()) ? parent.getMessage() : null) : child.getMessage();
            return new WorkflowRunResponse.Node(node.id(), node.taskId(), names.get(UUID.fromString(node.taskId())),
                    state, child == null ? null : TaskRunResponse.from(child), message);
        }).toList();
        return new WorkflowRunResponse(TaskRunResponse.from(parent), definition, nodes.size(),
                children.values().stream().filter(child -> child.getStatus() == TaskRunStatus.SUCCESS).count(),
                children.values().stream().anyMatch(child -> ACTIVE.contains(child.getStatus())), nodes);
    }
    private String projectedStatus(TaskRun parent, String nodeId) {
        if (Objects.equals(parent.getErrorNodeId(), nodeId)) return "FAILED";
        return switch (parent.getStatus()) {
            case FAILED, TIMED_OUT -> "BLOCKED";
            case CANCEL_REQUESTED, CANCELLED -> "CANCELLED";
            case SKIPPED -> "SKIPPED";
            default -> "WAITING";
        };
    }
    private Map<String, TaskRun> children(UUID parentId) {
        return runs.findAllByParentRunIdOrderByQueuedAtAsc(parentId).stream()
                .collect(Collectors.toMap(TaskRun::getWorkflowNodeId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }
    private static boolean dependenciesSucceeded(WorkflowDefinition definition, String nodeId, Map<String, TaskRun> children) {
        return definition.edges().stream().filter(edge -> edge.target().equals(nodeId)).allMatch(edge -> {
            var child = children.get(edge.source());
            return child != null && child.getStatus() == TaskRunStatus.SUCCESS;
        });
    }
    private boolean terminationUnconfirmed(TaskRun child) {
        return "EXECUTION_TERMINATION_UNCONFIRMED".equals(child.getErrorCode());
    }
    private TaskRun requireParent(UUID id, boolean lock) {
        var parent = (lock ? runs.findByIdForUpdate(id) : runs.findById(id)).orElseThrow(() -> missing("运行不存在"));
        if (parent.getTaskType() != TaskType.WORKFLOW) throw conflict("当前运行不是工作流");
        return parent;
    }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
