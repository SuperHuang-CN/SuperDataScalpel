package cn.superhuang.data.scalpel.business.dataentry.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "填报表单对发布、新增、编辑、删除和查询操作的独立可用性结论。")

public record DataEntryHealthResponse(
        @Schema(description = "当前配置和引用是否允许发布。")
        boolean canPublish,
        @Schema(description = "当前表单是否允许提交新增数据。")
        boolean canSubmit,
        @Schema(description = "当前表单是否允许编辑已有数据。")
        boolean canUpdateEntries,
        @Schema(description = "当前表单是否允许删除已有数据。")
        boolean canDeleteEntries,
        @Schema(description = "当前表单是否允许查询已有数据。")
        boolean canQueryEntries,
        @Schema(description = "支撑各 can* 结论的具体健康问题；没有问题时为空列表。")
        List<DataEntryHealthIssueResponse> issues
) {
    public DataEntryHealthResponse {
        issues = List.copyOf(issues);
    }
}
