package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("Canvas 4.62 起一张合并层的字段处理。第一张输入表是基准输入层，不允许在此配置。")
public record UnionMergeTable(
        @JsonPropertyDescription("必须是 inputTableNames 中除第一项外的合并层表名。")
        String tableName,
        @JsonPropertyDescription("字段自定义规则；空数组表示全部使用默认的同名 Match/原名追加。")
        List<UnionMergeFieldRule> fieldRules
) {
    public UnionMergeTable {
        fieldRules = fieldRules == null ? null : List.copyOf(fieldRules);
    }
}
