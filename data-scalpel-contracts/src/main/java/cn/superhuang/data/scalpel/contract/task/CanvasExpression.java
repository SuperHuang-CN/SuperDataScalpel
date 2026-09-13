package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonClassDescription("DERIVE_COLUMNS 中可递归组合的受控表达式联合，以 kind 判别字段引用、常量、算术二元运算、白名单函数、CASE_WHEN 或执行级运行时值。它不接受任意 SQL、函数名、Java 类或客户端变量；字段引用始终解析原始来源 Schema。")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ColumnExpression.class, name = "COLUMN"),
        @JsonSubTypes.Type(value = LiteralExpression.class, name = "LITERAL"),
        @JsonSubTypes.Type(value = BinaryExpression.class, name = "BINARY"),
        @JsonSubTypes.Type(value = FunctionExpression.class, name = "FUNCTION"),
        @JsonSubTypes.Type(value = CaseWhenExpression.class, name = "CASE_WHEN"),
        @JsonSubTypes.Type(value = RuntimeValueExpression.class, name = "RUNTIME_VALUE")
})
public sealed interface CanvasExpression
        permits ColumnExpression, LiteralExpression, BinaryExpression,
                FunctionExpression, CaseWhenExpression, RuntimeValueExpression {
}
