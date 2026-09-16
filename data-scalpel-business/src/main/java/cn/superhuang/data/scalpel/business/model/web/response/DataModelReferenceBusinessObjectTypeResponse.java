package cn.superhuang.data.scalpel.business.model.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "引用当前模型或字段的业务对象类型摘要")
public record DataModelReferenceBusinessObjectTypeResponse(
        @Schema(description = "业务对象类型 UUID") UUID id,
        @Schema(description = "业务对象类型显示名称") String name,
        @Schema(description = "业务对象类型唯一编码") String code
) {
}
