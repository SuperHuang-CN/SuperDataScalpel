package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "任务当前定义涉及的输入、输出模型关系。")

public record TaskModelRelationsResponse(
        @Schema(description = "所属任务 UUID。")
        UUID taskId,
        @Schema(description = "是否存在该任务类型的当前定义记录；true 不保证引用模型、资源或任务状态满足发布和运行条件。WORKFLOW 当前不展开子任务间接模型关系。")
        boolean configured,
        @Schema(description = "查询时当前定义版本；未配置时为空。历史运行使用其自身 definitionVersion 和运行快照。")
        Integer definitionVersion,
        @Schema(description = "当前定义直接引用的去重模型列表；READ_WRITE JAR 绑定会在同一模型的 locations 中同时产生 INPUT 和 OUTPUT，未配置或没有模型引用时为空列表。")
        List<TaskRelatedModelResponse> models
) {
}
