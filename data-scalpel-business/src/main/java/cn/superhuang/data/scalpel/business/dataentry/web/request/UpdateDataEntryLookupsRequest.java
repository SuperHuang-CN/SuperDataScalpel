package cn.superhuang.data.scalpel.business.dataentry.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(description = "整体替换表单字段关联模型下拉配置的请求")

public record UpdateDataEntryLookupsRequest(
        @Schema(description = "完整关联下拉列表；空数组表示清除全部配置，每个目标字段最多出现一次")
        List<@Valid LookupInput> lookups
) {
    public UpdateDataEntryLookupsRequest {
        lookups = lookups == null ? List.of() : List.copyOf(lookups);
    }

    @Schema(description = "一个目标字段与来源模型显示字段的关联")

    public record LookupInput(
            @Schema(description = "目标模型中需要显示关联下拉的字段 UUID")
            @NotNull UUID targetFieldId,
            @Schema(description = "提供选项的已发布来源模型 UUID；必须具有唯一的单字段业务主键")
            @NotNull UUID sourceModelId,
            @Schema(description = "来源模型中作为显示标签的非空 STRING 字段 UUID；实际保存值固定取来源业务主键")
            @NotNull UUID sourceLabelFieldId
    ) {
    }
}
