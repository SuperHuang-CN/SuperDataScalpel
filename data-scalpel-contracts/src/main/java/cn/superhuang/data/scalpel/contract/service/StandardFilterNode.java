package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Recursive condition group or leaf predicate in the standard table service V1 protocol. */
public record StandardFilterNode(
        @JsonPropertyDescription("叶子条件引用的服务字段编码；AND/OR 组合节点必须为空。")
        String field,
        @JsonPropertyDescription("过滤运算符；AND/OR 使用 conditions，其他运算符使用 field 和 value。")
        @NotNull StandardFilterOperator operator,
        @JsonPropertyDescription("叶子条件的比较值；IN、NOT_IN 和 BETWEEN 使用数组，无值运算符及组合节点为空。")
        Object value,
        @JsonPropertyDescription("当前 AND/OR 过滤组的子条件；叶子条件时为空。")
        List<@Valid StandardFilterNode> conditions
) {

    public StandardFilterNode {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }
}
