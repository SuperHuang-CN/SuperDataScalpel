package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "工作流定义的只读校验结果，不保存或运行工作流。")

public record WorkflowDefinitionValidationResponse(
        @Schema(description = "是否通过全部阻断性校验。")
        boolean valid,
        @Schema(description = "阻止工作流发布和运行的问题；通过时为空列表。保存接口仅检查 schemaVersion，因此这些问题不一定阻止草稿保存。")
        List<Problem> problems
) {
    @Schema(description = "工作流定义中的稳定校验问题及定位信息。")
    public record Problem(
            @Schema(description = "稳定问题码：WORKFLOW_VERSION_UNSUPPORTED、WORKFLOW_PARALLELISM_INVALID、WORKFLOW_EMPTY、WORKFLOW_NODE_ID_INVALID、WORKFLOW_TASK_REQUIRED、WORKFLOW_TASK_UNAVAILABLE、WORKFLOW_EDGE_INVALID 或 WORKFLOW_CYCLE。")
            String code,
            @Schema(description = "说明工作流定义违反哪项规则以及如何定位问题的可读信息。")
            String message,
            @Schema(description = "问题关联的工作流节点稳定 ID；工作流整体问题时为空，不是数据库 UUID。")
            String nodeId,
            @Schema(description = "问题关联边在 definition.edges 中的零基索引；非边问题为空。")
            Integer edgeIndex
    ) { }
}
