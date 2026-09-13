package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;

import java.util.List;
import java.util.UUID;

@Schema(description = "任务引用的模型及全部引用位置。")

public record TaskRelatedModelResponse(
        @Schema(description = "模型 UUID。")
        UUID modelId,
        @Schema(description = "模型稳定编码。")
        String modelCode,
        @Schema(description = "当前模型显示名称。")
        String modelName,
        @Schema(description = "查询时模型当前生命周期状态；是否可发布或运行由各任务类型在相应边界重新校验。")
        DataModelStatus modelStatus,
        @Schema(description = "模型物理表由平台托管还是引用外部表。")
        PhysicalTableMode physicalTableMode,
        @Schema(description = "查询时模型当前 Schema 版本；当前定义关系只保存模型身份，不代表历史运行固定版本。")
        int schemaVersion,
        @Schema(description = "该模型相对任务承担的输入或输出角色集合。")
        List<ModelTaskRelationRole> roles,
        @Schema(description = "该模型在任务定义中的全部引用位置。")
        List<TaskModelReferenceLocationResponse> locations
) {
}
