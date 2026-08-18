package cn.superhuang.data.scalpel.business.lineage.web.response;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageAssetKind;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageWriteMode;

import java.util.List;

public record TaskLineageFlowResponse(
        String flowKey,
        String outputLabel,
        LineageAssetKind outputKind,
        LineageWriteMode writeMode,
        LineageCoverage coverage,
        List<TaskLineageOutputFieldResponse> outputFields
) {
    public TaskLineageFlowResponse {
        outputFields = outputFields == null ? List.of() : List.copyOf(outputFields);
    }
}
