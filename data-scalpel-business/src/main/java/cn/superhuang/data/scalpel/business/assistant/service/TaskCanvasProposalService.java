package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSet;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSetStatus;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSetType;
import cn.superhuang.data.scalpel.business.assistant.repository.AssistantChangeSetRepository;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.service.CanvasTaskDefinitionService;
import cn.superhuang.data.scalpel.business.task.service.DataTaskService;
import cn.superhuang.data.scalpel.business.task.web.response.CanvasTaskDefinitionResponse;
import cn.superhuang.data.scalpel.business.task.web.response.DataTaskResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class TaskCanvasProposalService {

    private final AssistantTaskCanvasQueryService queryService;
    private final TaskCanvasDefinitionBuilder builder;
    private final DataTaskService taskService;
    private final CanvasTaskDefinitionService canvasService;
    private final DirectoryService directoryService;
    private final AssistantChangeSetRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public TaskCanvasProposalService(
            AssistantTaskCanvasQueryService queryService,
            TaskCanvasDefinitionBuilder builder,
            DataTaskService taskService,
            CanvasTaskDefinitionService canvasService,
            DirectoryService directoryService,
            AssistantChangeSetRepository repository,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.queryService = queryService;
        this.builder = builder;
        this.taskService = taskService;
        this.canvasService = canvasService;
        this.directoryService = directoryService;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public AssistantChangeSet propose(
            UUID sessionId,
            UUID runId,
            String username,
            TaskCanvasPlan plan
    ) {
        validateTarget(plan);
        Map<String, AssistantTaskCanvasQueryService.ResourceFingerprint> inputResources = new LinkedHashMap<>();
        Map<String, AssistantTaskCanvasQueryService.SafeSchema> inputSchemas = new LinkedHashMap<>();
        for (TaskCanvasPlan.Input input : plan.inputs()) {
            AssistantTaskCanvasQueryService.SafeSchema schema = queryService.fullSchema(input);
            AssistantTaskCanvasQueryService.ResourceFingerprint resource = new AssistantTaskCanvasQueryService.ResourceFingerprint(
                    schema.kind(), schema.resourceId(), schema.subResourceId(), schema.code(), schema.name(),
                    schema.fingerprint()
            );
            inputResources.put(input.ref(), resource);
            inputSchemas.put(input.ref(), schema);
        }
        List<AssistantTaskCanvasQueryService.ResourceFingerprint> resources = new ArrayList<>(inputResources.values());
        AssistantTaskCanvasQueryService.SafeSchema outputSchema = queryService.fullSchema(plan.output());
        resources.add(new AssistantTaskCanvasQueryService.ResourceFingerprint(
                outputSchema.kind(), outputSchema.resourceId(), outputSchema.subResourceId(), outputSchema.code(),
                outputSchema.name(), outputSchema.fingerprint()
        ));
        var definition = builder.build(plan, inputResources, inputSchemas, outputSchema);
        TaskCanvasProposalPayload.ExistingTaskSnapshot existingTask = existingTaskSnapshot(plan);
        String summary = normalizeSummary(plan.summary(), definition.nodes().size());
        List<String> needsUserInput = normalizedNeedsUserInput(plan);
        TaskCanvasProposalPayload payload = new TaskCanvasProposalPayload(
                plan, definition, resources, existingTask,
                plan.target().newTask(), summary, normalizeNotes(plan.assumptions()), needsUserInput,
                definition.nodes().size(), definition.edges().size()
        );
        return persist(sessionId, runId, username, summary, write(payload));
    }

    private AssistantChangeSet persist(
            UUID sessionId,
            UUID runId,
            String username,
            String summary,
            String payloadJson
    ) {
        return transactionTemplate.execute(status -> {
            List<AssistantChangeSet> pending = repository.findAllBySessionIdAndStatus(
                    sessionId, AssistantChangeSetStatus.PENDING
            );
            pending.forEach(AssistantChangeSet::supersede);
            repository.saveAll(pending);
            return repository.saveAndFlush(AssistantChangeSet.create(
                    sessionId, runId, username, AssistantChangeSetType.TASK_CANVAS, summary, payloadJson
            ));
        });
    }

    public TaskCanvasProposalPayload readPayload(AssistantChangeSet changeSet) {
        try {
            return objectMapper.readValue(changeSet.getPayloadJson(), TaskCanvasProposalPayload.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法读取任务 Canvas 提案", exception);
        }
    }

    public TaskCanvasApplicationResult readResult(AssistantChangeSet changeSet) {
        if (changeSet.getResultJson() == null) return null;
        try {
            return objectMapper.readValue(changeSet.getResultJson(), TaskCanvasApplicationResult.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法读取任务 Canvas 应用结果", exception);
        }
    }

    public AssistantChangeSet accept(UUID id, UUID actualTaskId, String username) {
        AssistantChangeSet initial = repository.findByIdAndOwnerUsername(id, username)
                .orElseThrow(() -> notFound("任务 Canvas 提案不存在"));
        if (initial.getChangeType() != AssistantChangeSetType.TASK_CANVAS) throw conflict("该变更计划不是任务 Canvas 提案");
        TaskCanvasProposalPayload payload = readPayload(initial);
        if (initial.getStatus() == AssistantChangeSetStatus.APPLIED) {
            TaskCanvasApplicationResult result = readResult(initial);
            if (result != null && result.taskId().equals(actualTaskId)) return initial;
            throw conflict("该提案已经应用到其他任务");
        }
        if (initial.getStatus() != AssistantChangeSetStatus.PENDING) throw conflict("只有待确认的提案可以应用");

        try {
            validateTaskForAccept(payload, actualTaskId);
        } catch (StaleProposalException exception) {
            markStale(id, username, exception.getMessage());
            throw stale(exception.getMessage());
        }
        // Resource reads, including JDBC metadata, intentionally happen before the short state transaction.
        try {
            if (!resourceFingerprintsMatch(payload)) {
                String reason = "资源已删除或逻辑 Schema 已变化，请重新生成提案";
                markStale(id, username, reason);
                throw stale(reason);
            }
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 404 || exception.getStatusCode().value() == 409) {
                String reason = "资源已删除、不可用或逻辑 Schema 已变化，请重新生成提案";
                markStale(id, username, reason);
                throw stale(reason);
            }
            throw exception;
        }
        return markApplied(id, actualTaskId, username, payload.definition());
    }

    private void validateTarget(TaskCanvasPlan plan) {
        if (plan == null || plan.target() == null) throw badRequest("Canvas 提案不能为空");
        if (plan.target().taskId() != null) {
            DataTaskResponse task = taskService.get(plan.target().taskId());
            requireReplaceableTask(task);
        } else {
            TaskCanvasPlan.NewTaskDraft draft = plan.target().newTask();
            if (draft == null) throw badRequest("新任务草稿不能为空");
            required(draft.name(), "任务名称", 100);
            optional(draft.description(), "任务说明", 1000);
            directoryService.validateAssignment(DirectoryScope.TASK, draft.directoryId());
        }
    }

    private TaskCanvasProposalPayload.ExistingTaskSnapshot existingTaskSnapshot(TaskCanvasPlan plan) {
        if (plan.target().taskId() == null) return null;
        DataTaskResponse task = taskService.get(plan.target().taskId());
        CanvasTaskDefinitionResponse definition = canvasService.get(task.id());
        return new TaskCanvasProposalPayload.ExistingTaskSnapshot(
                task.id(), task.name(), definition.version(), definition.updatedAt(), task.updatedAt(),
                definition.configured()
        );
    }

    private void validateTaskForAccept(TaskCanvasProposalPayload payload, UUID actualTaskId) {
        if (actualTaskId == null) throw badRequest("必须指定实际任务 ID");
        DataTaskResponse task = taskService.get(actualTaskId);
        requireReplaceableTask(task);
        CanvasTaskDefinitionResponse current = canvasService.get(actualTaskId);
        if (payload.existingTask() != null) {
            if (!payload.existingTask().taskId().equals(actualTaskId)) throw conflict("提案目标与实际任务不一致");
            if (current.version() != payload.existingTask().definitionVersion()
                    || !Objects.equals(current.updatedAt(), payload.existingTask().definitionUpdatedAt())) {
                throw new StaleProposalException("任务 Canvas 定义已经变化，请重新生成提案");
            }
        } else if (current.configured()) {
            throw new StaleProposalException("新建任务已经配置 Canvas，请重新生成提案");
        }
    }

    private boolean resourceFingerprintsMatch(TaskCanvasProposalPayload payload) {
        List<AssistantTaskCanvasQueryService.ResourceFingerprint> current = new ArrayList<>();
        for (TaskCanvasPlan.Input input : payload.plan().inputs()) current.add(queryService.fingerprint(input));
        current.add(queryService.fingerprint(payload.plan().output()));
        if (current.size() != payload.resources().size()) return false;
        for (int index = 0; index < current.size(); index++) {
            AssistantTaskCanvasQueryService.ResourceFingerprint left = current.get(index);
            AssistantTaskCanvasQueryService.ResourceFingerprint right = payload.resources().get(index);
            if (!left.kind().equals(right.kind())
                    || !left.resourceId().equals(right.resourceId())
                    || !Objects.equals(left.subResourceId(), right.subResourceId())
                    || !left.fingerprint().equals(right.fingerprint())) return false;
        }
        return true;
    }

    private AssistantChangeSet markApplied(
            UUID id,
            UUID taskId,
            String username,
            cn.superhuang.data.scalpel.contract.task.CanvasDefinition definition
    ) {
        return transactionTemplate.execute(status -> {
            AssistantChangeSet changeSet = repository.findLockedById(id)
                    .orElseThrow(() -> notFound("任务 Canvas 提案不存在"));
            if (!changeSet.getOwnerUsername().equals(username)) throw notFound("任务 Canvas 提案不存在");
            if (changeSet.getStatus() == AssistantChangeSetStatus.APPLIED) {
                TaskCanvasApplicationResult result = readResult(changeSet);
                if (result != null && result.taskId().equals(taskId)) return changeSet;
                throw conflict("该提案已经应用到其他任务");
            }
            if (changeSet.getStatus() != AssistantChangeSetStatus.PENDING) throw conflict("只有待确认的提案可以应用");
            changeSet.apply(username, write(new TaskCanvasApplicationResult(
                    taskId, "CLIENT_DRAFT", definition
            )), Instant.now());
            return repository.saveAndFlush(changeSet);
        });
    }

    private void markStale(UUID id, String username, String reason) {
        transactionTemplate.executeWithoutResult(status -> {
            AssistantChangeSet changeSet = repository.findLockedById(id)
                    .orElseThrow(() -> notFound("任务 Canvas 提案不存在"));
            if (!changeSet.getOwnerUsername().equals(username)) throw notFound("任务 Canvas 提案不存在");
            if (changeSet.getStatus() == AssistantChangeSetStatus.PENDING) {
                changeSet.markStale(reason);
                repository.saveAndFlush(changeSet);
            }
        });
    }

    private static void requireReplaceableTask(DataTaskResponse task) {
        if (task.type() != TaskType.SPARK_CANVAS) throw conflict("第一版只支持批 Canvas 任务");
        if (task.status() != TaskStatus.DRAFT && task.status() != TaskStatus.DISABLED) {
            throw conflict("只有草稿或已停用任务可以应用 Canvas 提案");
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法保存任务 Canvas 提案", exception);
        }
    }

    private static String normalizeSummary(String value, int nodeCount) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) return "任务 Canvas 提案（" + nodeCount + " 个节点）";
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private static List<String> normalizedNeedsUserInput(TaskCanvasPlan plan) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>(normalizeNotes(plan.needsUserInput()));
        if (plan.output().writeMode() == null) result.add("请选择输出写入模式");
        if (plan.output().columnMappings().isEmpty()) result.add("请检查并配置输出字段映射");
        if (plan.output().type() == TaskCanvasPlan.OutputType.JDBC_OUTPUT
                && plan.output().writeMode() == cn.superhuang.data.scalpel.contract.task.JdbcWriteMode.UPSERT
                && plan.output().upsertKeyColumns().isEmpty()) {
            result.add("请配置 JDBC UPSERT 唯一键字段");
        }
        return result.stream().limit(20).toList();
    }

    private static List<String> normalizeNotes(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty())
                .map(value -> value.length() <= 500 ? value : value.substring(0, 500))
                .distinct().limit(20).toList();
    }

    private static String required(String value, String label, int maximum) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw badRequest(label + "不能为空");
        if (normalized.length() > maximum) throw badRequest(label + "不能超过 " + maximum + " 个字符");
        return normalized;
    }

    private static String optional(String value, String label, int maximum) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maximum) throw badRequest(label + "不能超过 " + maximum + " 个字符");
        return normalized;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException stale(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static final class StaleProposalException extends RuntimeException {
        private StaleProposalException(String message) {
            super(message);
        }
    }
}
