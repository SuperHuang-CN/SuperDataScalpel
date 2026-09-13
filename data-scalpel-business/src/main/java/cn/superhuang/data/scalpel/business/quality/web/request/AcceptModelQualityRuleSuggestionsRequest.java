package cn.superhuang.data.scalpel.business.quality.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "批量采纳当前模型自动生成的质量规则建议；整批在一个事务中校验并创建。")
public record AcceptModelQualityRuleSuggestionsRequest(
        @Schema(description = "本次要采纳的建议 key，最多 100 个且不能重复；必须来自该模型当前建议列表，模型字段变化后应重新查询。采纳的规则初始均为停用状态。")
        @NotEmpty @Size(max = 100) List<@NotBlank String> suggestionKeys
) {
}
