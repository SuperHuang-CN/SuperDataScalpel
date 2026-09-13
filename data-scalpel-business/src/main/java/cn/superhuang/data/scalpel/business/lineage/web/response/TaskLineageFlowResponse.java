package cn.superhuang.data.scalpel.business.lineage.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageAssetKind;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageWriteMode;

import java.util.List;

@Schema(description = "任务中一个可独立选择的输出数据流及其目标和字段证据。")

public record TaskLineageFlowResponse(
        @Schema(description = "当前血缘快照内的输出流稳定键，用于 flowKey/selectedFlowKey 选择；在同一快照内唯一，不保证跨定义版本不变，也不是数据库 UUID。")
        String flowKey,
        @Schema(description = "用于界面和调用方区分多个输出流的安全显示标签。")
        String outputLabel,
        @Schema(description = "数据流写入目标的种类：MODEL 为纳管模型，JDBC_TABLE 为数据库表，EXTERNAL_RESOURCE 为其他外部资源。")
        LineageAssetKind outputKind,
        @Schema(description = "该流写入目标的方式，例如追加、全量覆盖、更新插入、分区覆盖、快照同步或新建。")
        LineageWriteMode writeMode,
        @Schema(description = "该数据流的血缘证据完整度；MODEL_ONLY 表示只有目标资产关系，FIELD_PARTIAL/COMPLETE 表示部分/全部输出字段可追溯。")
        LineageCoverage coverage,
        @Schema(description = "当前输出流能够确认的有序输出字段；只有资产级证据时为空列表。")
        List<TaskLineageOutputFieldResponse> outputFields
) {
    public TaskLineageFlowResponse {
        outputFields = outputFields == null ? List.of() : List.copyOf(outputFields);
    }
}
