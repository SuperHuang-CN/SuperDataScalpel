package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("对一个来源字段应用类型化比较的叶子条件。IS_NULL/IS_NOT_NULL 不带值，IN/NOT_IN 带 1 至 100 个值，其余操作符恰好一个值；Geometry 只允许空值判断，字符串匹配操作只接受 STRING 值。")
public record CanvasFieldPredicate(
        @JsonPropertyDescription("必填的来源字段名，按当前逻辑表 Schema 精确解析；不支持另一字段名或嵌套路径。")
        String columnName,
        @JsonPropertyDescription("必填运算符；决定 values 数量及适用类型。Geometry 仅支持 IS_NULL 和 IS_NOT_NULL，CONTAINS/STARTS_WITH/ENDS_WITH 的值必须为 STRING。")
        FilterOperator operator,
        @JsonPropertyDescription("类型化比较值数组：IS_NULL/IS_NOT_NULL 必须为空；IN/NOT_IN 为 1 至 100 项；其他运算符恰好一项。同一谓词的值类型必须一致且每项为可解析的非 NULL CanvasLiteral。")
        List<CanvasLiteral> values
) implements CanvasFilterCondition {
    public CanvasFieldPredicate {
        values = values == null ? null : List.copyOf(values);
    }
}
