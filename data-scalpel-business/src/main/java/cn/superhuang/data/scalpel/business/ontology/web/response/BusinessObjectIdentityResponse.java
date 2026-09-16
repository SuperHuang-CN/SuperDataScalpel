package cn.superhuang.data.scalpel.business.ontology.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "具体业务对象的本体身份。")
public record BusinessObjectIdentityResponse(
        @Schema(description = "对象类型 UUID，与业务唯一键共同组成对象身份。") UUID objectTypeId,
        @Schema(description = "对象类型稳定编码。") String objectTypeCode,
        @Schema(description = "对象类型显示名称。") String objectTypeName,
        @Schema(description = "业务唯一键，以字符串传输，避免整数精度损失。") String objectKey,
        @Schema(description = "对象显示名称。") String title
) {
}
