package cn.superhuang.data.scalpel.business.lineage.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;

import java.util.List;

@Schema(description = "围绕一个焦点资产或字段展开的有向血缘子图及证据质量。")

public record LineageGraphResponse(
        @Schema(description = "本次查询焦点对应的图节点稳定键；用于在 nodes 和 edges 中定位根节点，不保证是数据库 UUID。")
        String rootNodeId,
        @Schema(description = "血缘图粒度：TABLE 返回资产与任务之间的关系，FIELD 返回字段之间的派生关系。")
        LineageGranularity granularity,
        @Schema(description = "当前图的血缘证据完整度：MODEL_ONLY 仅能确认资产级关系，FIELD_PARTIAL 仅部分字段可追溯，FIELD_COMPLETE 表示已覆盖全部相关字段。")
        LineageCoverage coverage,
        @Schema(description = "图是否因节点、边或深度上限被截断；true 时不能据此断言不存在更多上下游关系。")
        boolean truncated,
        @Schema(description = "解释证据缺失、过期、降级或截断原因的非阻断告警。")
        List<String> warnings,
        @Schema(description = "图中的资产、任务、字段或数据服务节点；节点 id 供 rootNodeId 及边的 source/target 引用。")
        List<LineageGraphNodeResponse> nodes,
        @Schema(description = "图中的读取、写入、派生、字段影响或服务暴露关系；没有可确认关系时为空列表。")
        List<LineageGraphEdgeResponse> edges
) {
    public LineageGraphResponse {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }
}
