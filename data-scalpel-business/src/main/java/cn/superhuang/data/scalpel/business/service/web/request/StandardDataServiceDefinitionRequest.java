package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "标准表数据服务绑定的单个已发布模型；公开字段和查询能力从模型生成。")

public record StandardDataServiceDefinitionRequest(
        @Schema(description = "作为标准表查询来源的已发布模型 UUID。")
        @NotNull UUID modelId
) {
}
