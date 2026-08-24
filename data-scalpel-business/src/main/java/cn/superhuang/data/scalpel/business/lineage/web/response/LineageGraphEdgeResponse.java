package cn.superhuang.data.scalpel.business.lineage.web.response;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageFieldDerivationType;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageFieldUsageType;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageOutputFieldEffect;

import java.util.List;

public record LineageGraphEdgeResponse(
        String id,
        String source,
        String target,
        LineageGraphEdgeType type,
        LineageFieldDerivationType derivationType,
        LineageOutputFieldEffect outputEffect,
        List<LineageFieldUsageType> usages,
        List<String> focusFieldKeys
) {
    public LineageGraphEdgeResponse {
        usages = usages == null ? List.of() : List.copyOf(usages);
        focusFieldKeys = focusFieldKeys == null ? List.of() : List.copyOf(focusFieldKeys);
    }

    public LineageGraphEdgeResponse(
            String id, String source, String target, LineageGraphEdgeType type,
            LineageFieldDerivationType derivationType, LineageOutputFieldEffect outputEffect,
            List<LineageFieldUsageType> usages
    ) {
        this(id, source, target, type, derivationType, outputEffect, usages, List.of());
    }
}
