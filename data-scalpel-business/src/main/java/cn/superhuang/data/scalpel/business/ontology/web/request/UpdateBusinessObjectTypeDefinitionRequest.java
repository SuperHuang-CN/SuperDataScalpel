package cn.superhuang.data.scalpel.business.ontology.web.request;

import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectTypeDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "保存业务对象类型当前定义。保存后直接生效，允许暂未完成主来源等分步配置。")
public record UpdateBusinessObjectTypeDefinitionRequest(
        @Schema(description = "当前来源、属性、关系和能力定义。") @NotNull @Valid BusinessObjectTypeDefinition definition
) {
}
