package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
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
