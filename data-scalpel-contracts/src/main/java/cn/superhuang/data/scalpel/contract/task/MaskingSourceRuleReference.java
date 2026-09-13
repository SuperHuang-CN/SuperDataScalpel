package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("GLOBAL Canvas 脱敏规则保存时复制的来源身份快照，只用于展示、变化提示和审计；Task Engine 不用它查找或同步规则，也不据此决定执行参数。")
public record MaskingSourceRuleReference(
        @JsonPropertyDescription("必填的来源规则 UUID 字符串；保存只校验 UUID 格式，编译和运行不检查该规则是否仍存在。")
        String ruleId,
        @JsonPropertyDescription("必填的来源规则稳定编码快照；用于识别和展示，不作为执行参数。")
        String ruleCode,
        @JsonPropertyDescription("必填的来源规则显示名称快照；仅用于展示和审计，名称变化不改变执行。")
        String ruleName
) {
}
