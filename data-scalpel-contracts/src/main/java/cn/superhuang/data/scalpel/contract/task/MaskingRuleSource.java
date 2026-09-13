package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Canvas 脱敏规则来源标记：GLOBAL 要求保存全局规则 ID、编码和名称快照，但执行仍只使用内嵌 definition；INLINE 禁止 sourceRuleRef。全局规则后续修改或删除不会自动影响任务。")
public enum MaskingRuleSource {
    GLOBAL,
    INLINE
}
