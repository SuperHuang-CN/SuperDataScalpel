package cn.superhuang.data.scalpel.business.task.web.response;

import java.util.List;

public record WorkflowDefinitionValidationResponse(boolean valid, List<Problem> problems) {
    public record Problem(String code, String message, String nodeId, Integer edgeIndex) { }
}
