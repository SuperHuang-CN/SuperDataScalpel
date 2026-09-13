package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DropNullRowsRule.class, name = "DROP_ROW"),
        @JsonSubTypes.Type(value = FillNullLiteralRule.class, name = "FILL_LITERAL")
})
@JsonClassDescription("有序空值处理规则，以 kind 判别结构：DROP_ROW 按 ANY_NULL 或 ALL_NULL 删除指定字段命中 SQL NULL 的整行；FILL_LITERAL 只把单个字段的 SQL NULL 替换为同平台类型的非 NULL 常量。规则不把空字符串、NaN 或零值归一化为空。")
public sealed interface NullHandlingRule permits DropNullRowsRule, FillNullLiteralRule {
}
