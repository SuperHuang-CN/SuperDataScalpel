package cn.superhuang.data.scalpel.business.dataentry.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "尚未建立填报表单的模型及其当前已知适用性。")

public record DataEntryModelCandidateResponse(
        @Schema(description = "候选目标模型 UUID；创建表单时提交该值。")
        UUID modelId,
        @Schema(description = "模型稳定编码。")
        String modelCode,
        @Schema(description = "模型名称。")
        String modelName,
        @Schema(description = "模型查询时的生命周期状态：DRAFT、PUBLISHED 或 DISABLED；创建表单只要求模型存在，发布表单要求模型为 PUBLISHED。")
        String modelStatus,
        @Schema(description = "模型查询时的字段结构版本，从 1 开始并在字段结构实际变化时递增；表单发布时会固化该值。")
        int schemaVersion,
        @Schema(description = "轻量管理库检查已知条件下是否适合作为填报目标；true 不代表物理表、业务主键唯一性等外部数据库条件已经验证，创建后仍需详情健康检查。")
        boolean knownEligible,
        @Schema(description = "模型不适合作为填报目标的具体原因；knownEligible 为 true 时为空列表。")
        List<DataEntryHealthIssueResponse> issues
) {
    public DataEntryModelCandidateResponse {
        issues = List.copyOf(issues);
    }
}
