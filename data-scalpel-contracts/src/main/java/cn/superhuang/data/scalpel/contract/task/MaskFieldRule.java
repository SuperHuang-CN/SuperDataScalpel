package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("一个字段的脱敏规则；无论来源为 GLOBAL 还是 INLINE，内嵌 definition 都是保存、编译和运行的唯一执行配置。GLOBAL 引用不会在运行时读取、校验存在性或自动同步，规则变更和删除也不会改变已保存任务。")
public record MaskFieldRule(
        @JsonPropertyDescription("必填的来源字段名；必须存在且在同一操作中唯一。非 NULLIFY 策略要求 STRING，NULLIFY 要求字段可空；流式来源的事件时间字段禁止脱敏。")
        String fieldName,
        @JsonPropertyDescription("必填来源标记：GLOBAL 表示 definition 曾从平台规则复制并要求 sourceRuleRef；INLINE 表示任务自定义且 sourceRuleRef 必须为 NULL。该标记不改变执行算法。")
        MaskingRuleSource ruleSource,
        @JsonPropertyDescription("仅 GLOBAL 必填的来源规则身份快照；任务运行不据此查询规则。INLINE 必须为 NULL。")
        MaskingSourceRuleReference sourceRuleRef,
        @JsonPropertyDescription("必填且唯一生效的脱敏定义；strategy 决定允许哪些参数，未使用参数必须为空。真实运行失败时节点失败，不能回退输出原值。")
        MaskingRuleDefinition definition
) {
}
