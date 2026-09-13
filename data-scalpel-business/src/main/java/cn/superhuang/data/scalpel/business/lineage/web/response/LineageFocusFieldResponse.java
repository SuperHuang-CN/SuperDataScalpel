package cn.superhuang.data.scalpel.business.lineage.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;

import java.util.List;
import java.util.UUID;

@Schema(description = "本次字段血缘查询中的一个焦点字段及证据完整度。")

public record LineageFocusFieldResponse(
        @Schema(description = "字段在当前血缘快照中的稳定键，用于选择焦点和关联图中的 focusFieldKeys；不保证是数据库 UUID。")
        String fieldKey,
        @Schema(description = "能够解析到纳管模型字段时返回对应字段 UUID；外部字段或历史定义无法解析时为空。")
        UUID modelFieldId,
        @Schema(description = "字段稳定技术编码；外部或历史字段无法确认时为空。")
        String code,
        @Schema(description = "字段显示名称。")
        String name,
        @Schema(description = "同级展示顺序，数值越小越靠前。")
        int sortOrder,
        @Schema(description = "该字段的血缘证据完整度：MODEL_ONLY 仅有资产级关系，FIELD_PARTIAL 仅部分路径可追溯，FIELD_COMPLETE 表示字段路径完整。")
        LineageCoverage coverage,
        @Schema(description = "该焦点字段是否存在可展示的正式血缘关系。")
        boolean hasLineage,
        @Schema(description = "该字段的血缘关系是否因图规模上限被截断；true 时不能把当前图视为完整链路。")
        boolean truncated,
        @Schema(description = "解释该字段无血缘、降级或截断原因的非阻断告警。")
        List<String> warnings
) {
    public LineageFocusFieldResponse {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
