package cn.superhuang.data.scalpel.business.quality.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleDefinition;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleSeverity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "完整更新质量规则的名称、说明、严重程度和定义；不改变启用状态。模型内仍按名称及规则语义 key 分别判重，阈值变化不能用于创建同一逻辑目标的第二条同类型规则。")
public record UpdateModelQualityRuleRequest(
        @Schema(description = "规则展示名称；去除首尾空白后保存，在同一模型内忽略大小写唯一。")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "规则用途或判定依据说明；空白值按未填写处理。")
        @Size(max = 500) String description,
        @Schema(description = "规则未通过时的业务严重程度：CRITICAL、MAJOR 或 MINOR。")
        @NotNull ModelQualityRuleSeverity severity,
        @Schema(description = "新的多态规则定义；type 必须与该规则创建时的类型一致。更新成功会清除失效标记，但保留当前启用状态；原先因失效被自动停用的规则不会因此重新启用。")
        @NotNull @Valid ModelQualityRuleDefinition definition
) {
}
