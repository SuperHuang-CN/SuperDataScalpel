package cn.superhuang.data.scalpel.business.model.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "模型基础信息及当前完整字段结构")
public record DataModelDetailResponse(
        @Schema(description = "模型基础信息和物理位置") DataModelResponse model,
        @Schema(description = "按 sortOrder 排列的当前字段定义") List<DataModelFieldResponse> fields
) {
}
