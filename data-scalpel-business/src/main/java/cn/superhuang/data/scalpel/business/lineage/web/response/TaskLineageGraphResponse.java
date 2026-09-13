package cn.superhuang.data.scalpel.business.lineage.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;

import java.util.List;
import java.util.UUID;

@Schema(description = "任务当前未退役血缘快照中指定输出流或输出字段的资产/字段图；可能对应上次发布或最近成功运行摄取的版本，不等同于尚未重新发布的编辑中定义。")

public record TaskLineageGraphResponse(
        @Schema(description = "任务 UUID。")
        UUID taskId,
        @Schema(description = "生成当前血缘证据的任务定义版本；尚无可分析定义时为空。")
        Integer definitionVersion,
        @Schema(description = "该任务整体的血缘证据完整度；当任一数据流缺少字段证据时不会宣称 FIELD_COMPLETE。")
        LineageCoverage coverage,
        @Schema(description = "当前血缘快照能够确认的全部独立输出流摘要；graph 只展示 selectedFlowKey 对应的一条流。")
        List<TaskLineageFlowResponse> flows,
        @Schema(description = "本次返回图所对应的数据流键；应匹配 flows 中的 flowKey。")
        String selectedFlowKey,
        @Schema(description = "字段级便捷查询返回的首个聚焦输出字段键；显式指定一个字段时等于请求值，省略时可能是默认前 20 个聚焦字段中的第一个。表级图或没有输出字段时为空，不能据此判断图只含一个字段。")
        String selectedOutputFieldKey,
        @Schema(description = "选中数据流及输出字段对应的上游和下游血缘图。")
        LineageGraphResponse graph
) {
    public TaskLineageGraphResponse {
        flows = flows == null ? List.of() : List.copyOf(flows);
    }
}
