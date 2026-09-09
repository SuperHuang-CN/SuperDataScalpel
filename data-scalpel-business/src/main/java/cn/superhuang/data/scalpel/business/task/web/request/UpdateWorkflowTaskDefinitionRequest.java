package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.business.task.domain.WorkflowDefinition;
import jakarta.validation.constraints.NotNull;

public record UpdateWorkflowTaskDefinitionRequest(@NotNull WorkflowDefinition definition) { }
