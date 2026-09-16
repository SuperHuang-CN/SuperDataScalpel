package cn.superhuang.data.scalpel.business.ontology.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "沿一条业务关系读取的关联具体对象分页结果。")
public record BusinessObjectRelatedPreviewResponse(
        @Schema(description = "本次访问关系的定义摘要。") BusinessObjectRelationResponse relation,
        @Schema(description = "本页具体对象身份列表；无匹配时为空。") List<BusinessObjectPreviewCandidateResponse> items,
        @Schema(description = "当前页码，从 1 开始。") int pageNo,
        @Schema(description = "本页最大记录数，范围 1～100。") int pageSize,
        @Schema(description = "是否还有下一页。") boolean hasNext,
        @Schema(description = "本次读取发现的来源或关联异常；不代表全量数据检查结果。") List<BusinessObjectTypeIssueResponse> diagnostics
) {
    public BusinessObjectRelatedPreviewResponse {
        items = items == null ? List.of() : List.copyOf(items);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
