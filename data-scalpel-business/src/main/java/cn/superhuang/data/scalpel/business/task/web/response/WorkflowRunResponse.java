package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.WorkflowDefinition;
import java.util.List;
import java.util.UUID;

public record WorkflowRunResponse(TaskRunResponse run, WorkflowDefinition definition, int totalNodes,
                                  long succeededNodes, boolean hasActiveChildren, List<Node> nodes) {
    public record Node(String id, String taskId, String taskName, String status, TaskRunResponse childRun,
                       String message) { }
}
