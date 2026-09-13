package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "空间数据服务的当前定义，指定唯一提供空间要素的模型。")

public record SpatialDataServiceDefinitionResponse(
        @Schema(description = "空间服务发布的模型 UUID；模型必须满足空间候选项的可选择条件。")
        UUID modelId,
        @Schema(description = "该空间定义的版本号，初始为 1；绑定 modelId 实际变化时递增，并同时重置当前样式及其远端应用状态；重复保存相同模型不变。")
        int version
) {
}
