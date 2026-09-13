package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("Join 结果中的一个字段投影；数组顺序决定启用字段在结果中的顺序。配置可暂时设为 included=false 以保留编辑状态，但同一侧同一来源字段即使停用也不能重复配置。")
public record JoinOutputColumn(
        @JsonPropertyDescription("必填来源侧：LEFT 表示 leftTableName，RIGHT 表示 rightTableName。")
        JoinOutputColumnSource sourceSide,
        @JsonPropertyDescription("所选来源侧中的字段名；必须存在。同一 sourceSide 与 sourceColumnName 组合在 outputColumns 中最多出现一次。")
        String sourceColumnName,
        @JsonPropertyDescription("included=true 时使用的结果字段名；所有启用项按大小写不敏感规则必须唯一。included=false 时不输出此字段，当前名称不参与结果字段重名校验。")
        String outputColumnName,
        @JsonPropertyDescription("是否把该来源字段投影到结果。false 仅保留编辑配置，不会在输出 Schema 或数据中产生字段；整个列表至少有一项为 true。")
        boolean included
) {
}
