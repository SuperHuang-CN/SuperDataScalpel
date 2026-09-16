package cn.superhuang.data.scalpel.business.ontology.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "主来源中具体业务对象候选的分页结果。")
public record BusinessObjectPreviewCandidatesResponse(
        @Schema(description = "本页具体对象身份列表；无匹配时为空。") List<BusinessObjectPreviewCandidateResponse> items,
        @Schema(description = "当前页码，从 1 开始。") int pageNo,
        @Schema(description = "本页最大记录数，范围 1～100。") int pageSize,
        @Schema(description = "是否还有下一页。") boolean hasNext
) {
    public BusinessObjectPreviewCandidatesResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
