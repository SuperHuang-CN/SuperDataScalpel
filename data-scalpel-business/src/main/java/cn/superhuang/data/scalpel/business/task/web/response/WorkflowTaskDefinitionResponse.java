package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.task.domain.WorkflowDefinition;
import java.util.UUID;

@Schema(description = "工作流任务当前保存的有向图定义。")

public record WorkflowTaskDefinitionResponse(
        @Schema(description = "所属任务 UUID。")
        UUID taskId,
        @Schema(description = "当前定义版本；尚未配置时为空。")
        Integer version,
        @Schema(description = "工作流节点、依赖边和布局定义；尚未配置时返回 schemaVersion=1、maxParallelism=4 的空定义，而非 null。")
        WorkflowDefinition definition
) { }
