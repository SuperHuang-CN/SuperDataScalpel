package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Canvas 4.62 起针对一个合并层字段的 Merge Layers 规则。未出现在规则中的字段默认按同名 MATCH，否则以原名追加。")
public record UnionMergeFieldRule(
        @JsonPropertyDescription("合并层中的来源字段名；同一张表中不能重复。")
        String sourceColumnName,
        @JsonPropertyDescription("必填动作：MATCH、RENAME 或 REMOVE。")
        UnionMergeFieldAction action,
        @JsonPropertyDescription("MATCH 时为已有输出字段，RENAME 时为新输出字段，REMOVE 时必须为 null。")
        String targetColumnName
) {
}
