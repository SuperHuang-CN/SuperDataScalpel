package cn.superhuang.data.scalpel.business.dataentry.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryFormStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "填报表单绑定模型、发布版本、生命周期和当前健康问题摘要。")

public record DataEntryFormResponse(
        @Schema(description = "填报表单 UUID。")
        UUID id,
        @Schema(description = "表单固定绑定的目标模型 UUID；模型删除后仍保留该弱引用。")
        UUID modelId,
        @Schema(description = "目标模型当前稳定编码；模型已删除时为空。")
        String modelCode,
        @Schema(description = "目标模型当前显示名称；模型已删除时为空。")
        String modelName,
        @Schema(description = "目标模型当前业务说明；模型已删除或未填写时为空。")
        String modelDescription,
        @Schema(description = "目标模型当前生命周期状态；用于判断表单是否仍可发布或使用。")
        String modelStatus,
        @Schema(description = "目标模型查询时的字段结构版本，从 1 开始并在字段结构实际变化时递增；模型已删除时为空。应与 publishedModelSchemaVersion 比较判断已发布表单是否仍可提交。")
        Integer modelSchemaVersion,
        @Schema(description = "表单生命周期：DRAFT 可编辑但不能填报，PUBLISHED 可按健康状态使用，DISABLED 已停用。")
        DataEntryFormStatus status,
        @Schema(description = "最近一次发布表单时确认并固化的目标模型字段结构版本；从未发布时为空，停用后仍保留。它不随模型后续变化自动更新。")
        Integer publishedModelSchemaVersion,
        @Schema(description = "健康检查摘要：HEALTHY 详情实时检查未发现问题；CHECK_REQUIRED 已发现问题；DETAIL_CHECK_REQUIRED 列表只完成管理库轻量诊断，仍需查询详情执行外部数据库检查。具体能力以 issues 和健康详情为准。")
        String healthSummary,
        @Schema(description = "影响表单发布、提交、删除或查询的健康问题；没有问题时为空列表。")
        List<DataEntryHealthIssueResponse> issues,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public DataEntryFormResponse {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
