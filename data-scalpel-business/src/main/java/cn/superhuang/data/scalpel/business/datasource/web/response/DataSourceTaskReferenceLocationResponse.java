package cn.superhuang.data.scalpel.business.datasource.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "任务当前保存定义中引用该数据源的一个具体位置；用于展示依赖来源，不是任务运行访问日志。")
public record DataSourceTaskReferenceLocationResponse(
        @Schema(description = "数据源在该位置作为输入或输出") DataSourceTaskRelationRole role,
        @Schema(description = "直接引用数据源或经模型存储间接引用") DataSourceRelationKind relationKind,
        @Schema(description = "任务定义中引用的资源种类") DataSourceTaskResourceKind resourceKind,
        @Schema(description = "在当前定义版本内定位节点、输入序号或资源绑定的内部键；不同 taskType 的格式不同，不能作为跨版本或跨任务公共 ID。") String locationKey,
        @Schema(description = "供用户定位引用的节点名称、输入序号或绑定名称。") String locationLabel,
        @Schema(description = "引用目标的可读摘要，例如模型名、物理表、Topic、API 资源或文件路径；无法从定义安全解析时为空。") String resourceLabel,
        @Schema(description = "经模型间接引用时的模型 UUID；直接引用时为空") UUID modelId,
        @Schema(description = "经模型间接引用时的模型名称；直接引用时为空") String modelName
) {
}
