package cn.superhuang.data.scalpel.business.dataentry.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "填报候选模型的分层或数据存储筛选项，仅包含未绑定表单模型实际引用的对象。")
public record DataEntryCandidateFilterOptionResponse(
        @Schema(description = "分层或数据存储 UUID；用于对应筛选条件。") UUID id,
        @Schema(description = "显示名称；引用对象缺失时显示其 UUID。") String name
) {}
