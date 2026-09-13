package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;

import java.util.UUID;

@Schema(description = "任务当前定义引用的模型及查询时模型元数据摘要；名称和 schemaVersion 来自当前模型记录，不是历史运行快照。")

public record TaskModelReferenceResponse(
        @Schema(description = "模型 UUID。")
        UUID modelId,
        @Schema(description = "模型稳定编码。")
        String modelCode,
        @Schema(description = "模型当前显示名称。")
        String modelName,
        @Schema(description = "查询时模型当前 Schema 版本；任务定义通常只保存 modelId，发布或运行时会重新校验并在运行快照中固化实际版本。")
        int schemaVersion
) {

    public static TaskModelReferenceResponse from(DataModel model) {
        return new TaskModelReferenceResponse(model.getId(), model.getCode(), model.getName(), model.getSchemaVersion());
    }
}
