package cn.superhuang.data.scalpel.business.lineage.web.response;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;

import java.util.List;
import java.util.UUID;

public record TaskFieldLineageGraphResponse(
        UUID taskId,
        Integer definitionVersion,
        LineageCoverage coverage,
        List<TaskLineageFlowResponse> flows,
        String selectedFlowKey,
        LineageFieldGraphResponse fieldGraph
) {
    public TaskFieldLineageGraphResponse {
        flows = flows == null ? List.of() : List.copyOf(flows);
    }
}
