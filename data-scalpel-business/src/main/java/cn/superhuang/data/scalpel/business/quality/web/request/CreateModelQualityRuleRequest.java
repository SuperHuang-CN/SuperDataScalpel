package cn.superhuang.data.scalpel.business.quality.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleDefinition;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleSeverity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "为一个模型创建质量规则；名称忽略大小写唯一，规则类型与逻辑检查目标形成的语义 key 也唯一。多数规则的阈值、边界或格式参数不属于语义 key，因此不能靠修改这些参数为同一字段并存两条同类型规则。")
public record CreateModelQualityRuleRequest(
        @Schema(description = "规则展示名称；去除首尾空白后保存，在同一模型内忽略大小写唯一。")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "规则用途或判定依据说明；空白值按未填写处理。")
        @Size(max = 500) String description,
        @Schema(description = "规则未通过时的业务严重程度：CRITICAL、MAJOR 或 MINOR；不决定任务运行是否技术成功。")
        @NotNull ModelQualityRuleSeverity severity,
        @Schema(description = "是否立即参与后续质检运行；停用规则会作为 SKIPPED/RULE_DISABLED 写入运行快照。")
        boolean enabled,
        @Schema(description = "带 type 判别字段的多态规则定义；字段及引用目标必须在保存时结构兼容，规则类型创建后不可修改。码表当前停用、引用模型的数据源不可读等运行依赖不会阻止保存，但会使后续运行跳过该规则。")
        @NotNull @Valid ModelQualityRuleDefinition definition
) {
}
