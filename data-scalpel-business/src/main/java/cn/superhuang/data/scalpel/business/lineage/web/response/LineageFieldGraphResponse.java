package cn.superhuang.data.scalpel.business.lineage.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "一个或多个焦点字段的上下游字段图及焦点字段状态。")

public record LineageFieldGraphResponse(
        @Schema(description = "当前焦点字段的上游和下游字段血缘图。")
        LineageGraphResponse graph,
        @Schema(description = "本次查询选中的焦点字段、证据粒度和是否存在正式关系。")
        List<LineageFocusFieldResponse> focusFields
) {
    public LineageFieldGraphResponse {
        focusFields = focusFields == null ? List.of() : List.copyOf(focusFields);
    }
}
