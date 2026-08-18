package cn.superhuang.data.scalpel.business.lineage.web.response;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageOutputFieldEffect;

import java.util.UUID;

public record TaskLineageOutputFieldResponse(
        String fieldKey,
        UUID modelFieldId,
        String code,
        String name,
        int sortOrder,
        LineageOutputFieldEffect outputEffect
) {
}
