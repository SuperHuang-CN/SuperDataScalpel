package cn.superhuang.data.scalpel.business.dataentry.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "数据填报表单、字段、关联模型下拉项和当前健康状态的完整详情。")

public record DataEntryFormDetailResponse(
        @Schema(description = "表单及其绑定模型、发布版本和当前状态摘要。")
        DataEntryFormResponse form,
        @Schema(description = "表单当前模型版本的可填报字段，按 sortOrder 排列并包含输入来源。")
        List<DataEntryFieldResponse> fields,
        @Schema(description = "表单中全部关联模型下拉配置；没有 MODEL_LOOKUP 字段时为空列表。")
        List<DataEntryLookupResponse> lookups,
        @Schema(description = "依据当前模型状态、Schema 版本、字典及关联模型引用计算的可发布和可操作结果。")
        DataEntryHealthResponse health
) {
    public DataEntryFormDetailResponse {
        fields = List.copyOf(fields);
        lookups = List.copyOf(lookups);
    }
}
