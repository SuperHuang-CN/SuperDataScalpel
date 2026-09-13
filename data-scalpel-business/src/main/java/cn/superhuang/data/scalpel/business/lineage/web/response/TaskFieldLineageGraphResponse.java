package cn.superhuang.data.scalpel.business.lineage.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;

import java.util.List;
import java.util.UUID;

@Schema(description = "任务当前未退役血缘快照中一条输出流的字段级血缘图和全部可选输出流摘要。")

public record TaskFieldLineageGraphResponse(
        @Schema(description = "任务 UUID。")
        UUID taskId,
        @Schema(description = "生成当前血缘证据的任务定义版本；尚无可分析定义时为空。")
        Integer definitionVersion,
        @Schema(description = "该任务字段血缘的整体证据完整度；MODEL_ONLY 表示尚无可靠字段关系，FIELD_PARTIAL/COMPLETE 表示部分/全部字段可追溯。")
        LineageCoverage coverage,
        @Schema(description = "当前血缘快照能够确认的全部独立输出流摘要；fieldGraph 只对应 selectedFlowKey。")
        List<TaskLineageFlowResponse> flows,
        @Schema(description = "本次返回字段图所对应的数据流键；应匹配 flows 中的 flowKey。")
        String selectedFlowKey,
        @Schema(description = "选中数据流的字段级节点和关系，以及可供继续聚焦查询的输出字段。")
        LineageFieldGraphResponse fieldGraph
) {
    public TaskFieldLineageGraphResponse {
        flows = flows == null ? List.of() : List.copyOf(flows);
    }
}
