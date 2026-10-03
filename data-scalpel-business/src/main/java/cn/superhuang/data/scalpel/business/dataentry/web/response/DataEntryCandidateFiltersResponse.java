package cn.superhuang.data.scalpel.business.dataentry.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "全部未绑定填报表单的 MANAGED 受管模型的筛选选项，不受候选分页或关键词限制，不连接外部数据库。")
public record DataEntryCandidateFiltersResponse(
        @Schema(description = "实际使用的分层，包括用户自建和已停用分层；未分层由 hasUnlayered 表达。")
        List<DataEntryCandidateFilterOptionResponse> layers,
        @Schema(description = "未绑定受管模型实际引用的数据源，包括已停用但仍被引用的来源；不包含仅由逻辑注册模型引用的来源。")
        List<DataEntryCandidateFilterOptionResponse> storages,
        @Schema(description = "是否存在未分层的未绑定模型。") boolean hasUnlayered
) {}
