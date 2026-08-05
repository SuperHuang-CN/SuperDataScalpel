package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DropNullRowsRule.class, name = "DROP_ROW"),
        @JsonSubTypes.Type(value = FillNullLiteralRule.class, name = "FILL_LITERAL")
})
public sealed interface NullHandlingRule permits DropNullRowsRule, FillNullLiteralRule {
}
