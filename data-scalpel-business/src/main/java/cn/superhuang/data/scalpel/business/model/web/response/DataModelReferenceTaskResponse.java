package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.web.response.ModelTaskReferenceType;
import cn.superhuang.data.scalpel.business.task.web.response.ModelTaskRelationRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "当前已保存任务定义或当前未退役血缘中引用模型的一个位置；同一任务可因多个角色、节点、绑定或输出流返回多项。")
public record DataModelReferenceTaskResponse(
        @Schema(description = "任务 UUID") UUID id,
        @Schema(description = "任务显示名称") String name,
        @Schema(description = "引用当前模型的任务类型") TaskType type,
        @Schema(description = "任务当前生命周期状态") TaskStatus status,
        @Schema(description = "当前模型在任务中的 INPUT 或 OUTPUT 角色") ModelTaskRelationRole role,
        @Schema(description = "Local SQL 显式输入/输出或 Canvas 模型节点等引用类型") ModelTaskReferenceType referenceType,
        @Schema(description = "Canvas 节点 UUID；非 Canvas 引用时为空") UUID nodeId,
        @Schema(description = "Canvas 时为节点名称；Spark JAR 资源绑定时为 bindingName；CURRENT_LINEAGE 时为 flowKey；其他引用可能为空。") String nodeName
) {
}
