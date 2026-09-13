package cn.superhuang.data.scalpel.business.lineage.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageOutputFieldEffect;

import java.util.UUID;

@Schema(description = "任务输出流中的一个字段及其写入效果和稳定选择键。")

public record TaskLineageOutputFieldResponse(
        @Schema(description = "输出字段在当前任务血缘快照中的稳定键，用于 selectedOutputFieldKey 和字段图查询；不保证是数据库 UUID。")
        String fieldKey,
        @Schema(description = "输出能够解析到纳管模型字段时返回对应字段 UUID；外部字段或历史定义无法解析时为空。")
        UUID modelFieldId,
        @Schema(description = "输出字段稳定技术编码；外部或历史字段无法确认时为空。")
        String code,
        @Schema(description = "输出字段显示名称。")
        String name,
        @Schema(description = "同级展示顺序，数值越小越靠前。")
        int sortOrder,
        @Schema(description = "任务对该输出字段的处理结果，区分来源派生、未知来源写入、常量、默认值、补空、保留原值和未写入。")
        LineageOutputFieldEffect outputEffect
) {
}
