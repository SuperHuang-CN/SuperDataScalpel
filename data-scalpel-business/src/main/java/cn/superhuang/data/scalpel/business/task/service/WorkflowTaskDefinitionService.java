package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.business.task.repository.*;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateWorkflowTaskDefinitionRequest;
import cn.superhuang.data.scalpel.business.task.web.response.*;
import cn.superhuang.data.scalpel.business.task.web.response.WorkflowDefinitionValidationResponse.Problem;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Service
public class WorkflowTaskDefinitionService {
    private final DataTaskRepository tasks;
    private final WorkflowTaskDefinitionRepository definitions;
    private final ObjectMapper mapper;
    public WorkflowTaskDefinitionService(DataTaskRepository tasks, WorkflowTaskDefinitionRepository definitions,
                                         ObjectMapper mapper) {
        this.tasks = tasks;
        this.definitions = definitions;
        this.mapper = mapper;
    }
    @Transactional(readOnly = true)
    public WorkflowTaskDefinitionResponse get(UUID id) {
        requireWorkflow(id, false);
        return definitions.findByTaskId(id).map(d -> new WorkflowTaskDefinitionResponse(id, d.getVersion(), read(d.getDefinitionJson())))
                .orElseGet(() -> new WorkflowTaskDefinitionResponse(id, null, WorkflowDefinition.empty()));
    }
    @Transactional
    public WorkflowTaskDefinitionResponse update(UUID id, UpdateWorkflowTaskDefinitionRequest request) {
        var task = requireWorkflow(id, true);
        if (task.getStatus() == TaskStatus.PUBLISHED) throw conflict("已发布任务请先停用后修改定义");
        var definition = request.definition();
        if (definition.schemaVersion() != 1) throw conflict("不支持此工作流定义版本");
        String json = mapper.writeValueAsString(definition);
        var saved = definitions.findByTaskId(id).orElseGet(() -> WorkflowTaskDefinition.create(id, json));
        if (!mapper.readTree(saved.getDefinitionJson()).equals(mapper.readTree(json))) saved.update(json);
        definitions.saveAndFlush(saved);
        return new WorkflowTaskDefinitionResponse(id, saved.getVersion(), definition);
    }
    @Transactional(readOnly = true)
    public WorkflowDefinitionValidationResponse validate(UUID id) {
        requireWorkflow(id, false);
        return validateDefinition(get(id).definition());
    }
    public WorkflowDefinitionValidationResponse validateDefinition(WorkflowDefinition definition) {
        var problems = new ArrayList<Problem>();
        if (definition.schemaVersion() != 1) problems.add(new Problem("WORKFLOW_VERSION_UNSUPPORTED", "不支持此工作流定义版本", null, null));
        if (definition.maxParallelism() < 1) problems.add(new Problem("WORKFLOW_PARALLELISM_INVALID", "最大并行度必须为正整数", null, null));
        if (definition.nodes().isEmpty()) problems.add(new Problem("WORKFLOW_EMPTY", "至少添加一个任务节点", null, null));
        var ids = new LinkedHashSet<String>();
        var taskIds = new HashMap<String, UUID>();
        for (var node : definition.nodes()) {
            if (node.id() == null || node.id().isBlank() || !node.id().equals(node.id().trim()) || node.id().length() > 100 || !ids.add(node.id()))
                problems.add(new Problem("WORKFLOW_NODE_ID_INVALID", "节点 ID 必须唯一且为 1～100 个字符", node.id(), null));
            try { taskIds.put(node.id(), UUID.fromString(node.taskId())); }
            catch (IllegalArgumentException | NullPointerException e) {
                problems.add(new Problem("WORKFLOW_TASK_REQUIRED", "请选择有效的引用任务", node.id(), null));
            }
        }
        var referenced = new HashMap<UUID, DataTask>();
        tasks.findAllById(taskIds.values()).forEach(t -> referenced.put(t.getId(), t));
        for (var entry : taskIds.entrySet()) {
            var task = referenced.get(entry.getValue());
            if (task == null || task.getStatus() != TaskStatus.PUBLISHED || !eligible(task.getType()))
                problems.add(new Problem("WORKFLOW_TASK_UNAVAILABLE", "引用任务必须是已发布的批任务", entry.getKey(), null));
        }
        var degrees = new HashMap<String, Integer>();
        var successors = new HashMap<String, List<String>>();
        ids.forEach(id -> { degrees.put(id, 0); successors.put(id, new ArrayList<>()); });
        var edges = new HashSet<WorkflowDefinition.Edge>();
        for (int i = 0; i < definition.edges().size(); i++) {
            var edge = definition.edges().get(i);
            if (!ids.contains(edge.source()) || !ids.contains(edge.target())
                    || Objects.equals(edge.source(), edge.target()) || !edges.add(edge)) {
                problems.add(new Problem("WORKFLOW_EDGE_INVALID", "连线端点无效、自环或重复连线", null, i));
                continue;
            }
            degrees.compute(edge.target(), (key, value) -> value + 1);
            successors.get(edge.source()).add(edge.target());
        }
        var ready = new ArrayDeque<String>();
        degrees.forEach((id, degree) -> { if (degree == 0) ready.add(id); });
        int visited = 0;
        while (!ready.isEmpty()) {
            var id = ready.remove(); visited++;
            for (String target : successors.get(id)) if (degrees.compute(target, (k, v) -> v - 1) == 0) ready.add(target);
        }
        if (visited != ids.size()) problems.add(new Problem("WORKFLOW_CYCLE", "工作流存在循环依赖", null, null));
        return new WorkflowDefinitionValidationResponse(problems.isEmpty(), List.copyOf(problems));
    }
    @Transactional
    public void publish(UUID id, TaskStatus expected) {
        var task = requireWorkflow(id, true);
        if (task.getStatus() != expected) throw conflict("任务状态已变化，请刷新后重试");
        var validation = validate(id);
        if (!validation.valid()) throw conflict(validation.problems().getFirst().message());
        task.publish();
    }
    @Transactional
    public void disable(UUID id) {
        var task = requireWorkflow(id, true);
        if (task.getStatus() != TaskStatus.PUBLISHED) throw conflict("只有已发布任务可以停用");
        task.disable();
    }
    @Transactional
    public void deleteDefinition(UUID id) { definitions.findByTaskId(id).ifPresent(definitions::delete); }
    public WorkflowDefinition read(String json) { return mapper.readValue(json, WorkflowDefinition.class); }
    public static boolean eligible(TaskType type) {
        return type == TaskType.LOCAL_SQL || type == TaskType.SPARK_CANVAS
                || type == TaskType.SPARK_MODEL_QUALITY || type == TaskType.SPARK_JAR;
    }
    private DataTask requireWorkflow(UUID id, boolean lock) {
        var task = (lock ? tasks.findByIdForUpdate(id) : tasks.findById(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (task.getType() != TaskType.WORKFLOW) throw conflict("当前任务不是工作流");
        return task;
    }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
