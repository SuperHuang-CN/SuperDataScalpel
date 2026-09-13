package cn.superhuang.data.scalpel.business.dataentry.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "填报表单、目标模型、物理表、字典或关联模型检查发现的一条问题。")

public record DataEntryHealthIssueResponse(
        @Schema(description = "便于客户端分类和引导修复的稳定问题编码。")
        String code,
        @Schema(description = "说明表单、目标模型、物理表、字典或关联配置异常的可读信息。")
        String message,
        @Schema(description = "该问题禁止或限制的操作标识，例如 PUBLISH、SUBMIT、DELETE_ENTRIES 或 QUERY_ENTRIES。")
        List<String> affectedOperations,
        @Schema(description = "问题直接涉及的目标模型字段 UUID；模型级问题为空。")
        UUID fieldId,
        @Schema(description = "关联查找问题涉及的来源模型 UUID；非关联模型问题为空。")
        UUID sourceModelId
) {
    public DataEntryHealthIssueResponse {
        affectedOperations = affectedOperations == null ? List.of() : List.copyOf(affectedOperations);
    }
}
