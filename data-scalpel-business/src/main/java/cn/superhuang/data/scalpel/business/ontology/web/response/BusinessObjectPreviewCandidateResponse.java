package cn.superhuang.data.scalpel.business.ontology.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "一个可选择的具体业务对象候选。")
public record BusinessObjectPreviewCandidateResponse(
        @Schema(description = "按字符串表现的业务唯一标识。") String objectKey,
        @Schema(description = "对象显示名称。") String title
) {
}
