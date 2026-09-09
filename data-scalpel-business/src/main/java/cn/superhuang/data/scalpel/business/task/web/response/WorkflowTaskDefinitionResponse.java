package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.WorkflowDefinition;
import java.util.UUID;

public record WorkflowTaskDefinitionResponse(UUID taskId, Integer version, WorkflowDefinition definition) { }
