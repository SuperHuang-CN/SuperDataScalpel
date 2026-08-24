package cn.superhuang.data.scalpel.business.lineage.web.response;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;

import java.util.List;
import java.util.UUID;

public record LineageFocusFieldResponse(
        String fieldKey,
        UUID modelFieldId,
        String code,
        String name,
        int sortOrder,
        LineageCoverage coverage,
        boolean hasLineage,
        boolean truncated,
        List<String> warnings
) {
    public LineageFocusFieldResponse {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
