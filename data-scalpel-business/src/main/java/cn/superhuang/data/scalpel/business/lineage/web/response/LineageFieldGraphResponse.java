package cn.superhuang.data.scalpel.business.lineage.web.response;

import java.util.List;

public record LineageFieldGraphResponse(
        LineageGraphResponse graph,
        List<LineageFocusFieldResponse> focusFields
) {
    public LineageFieldGraphResponse {
        focusFields = focusFields == null ? List.of() : List.copyOf(focusFields);
    }
}
