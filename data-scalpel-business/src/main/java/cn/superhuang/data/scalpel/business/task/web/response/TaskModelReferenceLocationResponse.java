package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "模型在任务定义中的一个引用位置。")

public record TaskModelReferenceLocationResponse(
        @Schema(description = "该引用位置相对任务的输入或输出角色。")
        ModelTaskRelationRole role,
        @Schema(description = "产生引用的定义类型或节点类型。")
        ModelTaskReferenceType referenceType,
        @Schema(description = "引用顺序，从 1 开始；当前仅 LOCAL_SQL_INPUT 提供，其他引用位置为空。")
        Integer ordinal,
        @Schema(description = "关联 Canvas 节点 UUID；当前仅 referenceType=CANVAS_NODE 时非空，其他引用类型为空。")
        UUID nodeId,
        @Schema(description = "CANVAS_NODE 时为节点当前名称；SPARK_JAR_RESOURCE_BINDING 时复用此字段返回 bindingName；其他类型为空。")
        String nodeName
) {
}
