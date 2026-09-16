package cn.superhuang.data.scalpel.business.ontology.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "从对象主来源分页读取候选记录的只读请求。")
public record BusinessObjectPreviewCandidatesRequest(
        @Schema(description = "页码，从 1 开始；省略时为 1。") @Min(1) Integer pageNo,
        @Schema(description = "每页候选数，范围 1 到 100；省略时为 20。") @Min(1) @Max(100) Integer pageSize
) {
}
