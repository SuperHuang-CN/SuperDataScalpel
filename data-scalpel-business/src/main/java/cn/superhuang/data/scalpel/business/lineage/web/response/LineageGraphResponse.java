package cn.superhuang.data.scalpel.business.lineage.web.response;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;

import java.util.List;

public record LineageGraphResponse(
        String rootNodeId,
        LineageGranularity granularity,
        LineageCoverage coverage,
        boolean truncated,
        List<String> warnings,
        List<LineageGraphNodeResponse> nodes,
        List<LineageGraphEdgeResponse> edges
) {
    public LineageGraphResponse {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }
}
