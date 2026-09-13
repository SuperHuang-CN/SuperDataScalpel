package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "标准表查询数据服务的当前定义，指定唯一对外查询模型。")

public record StandardDataServiceDefinitionResponse(
        @Schema(description = "标准查询服务暴露的数据模型 UUID。")
        UUID modelId,
        @Schema(description = "该标准表定义的版本号，初始为 1；绑定 modelId 实际变化时递增，重复保存相同模型不变。")
        int version
) {
}
