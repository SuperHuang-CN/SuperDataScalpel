package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = WindowFunctionItem.RowNumber.class, name = "ROW_NUMBER"),
        @JsonSubTypes.Type(value = WindowFunctionItem.Rank.class, name = "RANK"),
        @JsonSubTypes.Type(value = WindowFunctionItem.DenseRank.class, name = "DENSE_RANK"),
        @JsonSubTypes.Type(value = WindowFunctionItem.Lag.class, name = "LAG"),
        @JsonSubTypes.Type(value = WindowFunctionItem.Lead.class, name = "LEAD"),
        @JsonSubTypes.Type(value = WindowFunctionItem.Count.class, name = "COUNT"),
        @JsonSubTypes.Type(value = WindowFunctionItem.Sum.class, name = "SUM"),
        @JsonSubTypes.Type(value = WindowFunctionItem.Avg.class, name = "AVG"),
        @JsonSubTypes.Type(value = WindowFunctionItem.Min.class, name = "MIN"),
        @JsonSubTypes.Type(value = WindowFunctionItem.Max.class, name = "MAX"),
        @JsonSubTypes.Type(value = WindowFunctionItem.FirstValue.class, name = "FIRST_VALUE"),
        @JsonSubTypes.Type(value = WindowFunctionItem.LastValue.class, name = "LAST_VALUE")
})
@JsonClassDescription("窗口计算中的一个输出函数；kind 决定排名、前后偏移或带 ROWS Frame 的聚合取值语义。")
public sealed interface WindowFunctionItem permits
        WindowFunctionItem.RowNumber,
        WindowFunctionItem.Rank,
        WindowFunctionItem.DenseRank,
        WindowFunctionItem.Lag,
        WindowFunctionItem.Lead,
        WindowFunctionItem.Count,
        WindowFunctionItem.Sum,
        WindowFunctionItem.Avg,
        WindowFunctionItem.Min,
        WindowFunctionItem.Max,
        WindowFunctionItem.FirstValue,
        WindowFunctionItem.LastValue {

    String outputColumnName();

    @JsonClassDescription("窗口函数分支：按分区内声明的排序从 1 开始为每行分配连续序号。")

    record RowNumber(
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName
    ) implements WindowFunctionItem {
    }

    @JsonClassDescription("窗口函数分支：按排序值为每行计算名次；并列值名次相同，后续名次保留间隔。")

    record Rank(
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName
    ) implements WindowFunctionItem {
    }

    @JsonClassDescription("窗口函数分支：按排序值为每行计算稠密名次；并列值名次相同，后续名次不留间隔。")

    record DenseRank(
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName
    ) implements WindowFunctionItem {
    }

    @JsonClassDescription("窗口函数分支：读取分区排序中当前行之前指定偏移的字段值，不存在时使用可选默认值。")

    record Lag(
            @JsonPropertyDescription("来源字段名；必须存在于当前操作所引用的上游逻辑表 Schema 中。")
            String sourceColumnName,
            @JsonPropertyDescription("向前读取的行数，范围 1 到 10000；1 表示上一行。")
            int offset,
            @JsonPropertyDescription("前方不存在足够记录时返回的类型化默认值；为空时返回 NULL。")
            CanvasLiteral defaultValue,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName
    ) implements WindowFunctionItem {
    }

    @JsonClassDescription("窗口函数分支：读取分区排序中当前行之后指定偏移的字段值，不存在时使用可选默认值。")

    record Lead(
            @JsonPropertyDescription("来源字段名；必须存在于当前操作所引用的上游逻辑表 Schema 中。")
            String sourceColumnName,
            @JsonPropertyDescription("向后读取的行数，范围 1 到 10000；1 表示下一行。")
            int offset,
            @JsonPropertyDescription("后方不存在足够记录时返回的类型化默认值；为空时返回 NULL。")
            CanvasLiteral defaultValue,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName
    ) implements WindowFunctionItem {
    }

    @JsonClassDescription("窗口函数分支：统计当前行窗口 Frame 内来源字段的非 NULL 值数量。")

    record Count(
            @JsonPropertyDescription("要统计非 NULL 值的来源字段名；使用 null 表示 COUNT(*)，空字符串无效。")
            String sourceColumnName,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName,
            @JsonPropertyDescription("当前行参与 COUNT 计算的必填 ROWS Frame；起止边界均包含。")
            RowsWindowFrame frame
    ) implements WindowFunctionItem {
    }

    @JsonClassDescription("窗口函数分支：计算当前行窗口 Frame 内数值来源字段的合计值。")

    record Sum(
            @JsonPropertyDescription("来源字段名；必须存在于当前操作所引用的上游逻辑表 Schema 中。")
            String sourceColumnName,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName,
            @JsonPropertyDescription("当前行参与 SUM 计算的必填 ROWS Frame；起止边界均包含。")
            RowsWindowFrame frame
    ) implements WindowFunctionItem {
    }

    @JsonClassDescription("窗口函数分支：计算当前行窗口 Frame 内数值来源字段的平均值。")

    record Avg(
            @JsonPropertyDescription("来源字段名；必须存在于当前操作所引用的上游逻辑表 Schema 中。")
            String sourceColumnName,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName,
            @JsonPropertyDescription("当前行参与 AVG 计算的必填 ROWS Frame；起止边界均包含。")
            RowsWindowFrame frame
    ) implements WindowFunctionItem {
    }

    @JsonClassDescription("窗口函数分支：计算当前行窗口 Frame 内来源字段的最小值。")

    record Min(
            @JsonPropertyDescription("来源字段名；必须存在于当前操作所引用的上游逻辑表 Schema 中。")
            String sourceColumnName,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName,
            @JsonPropertyDescription("当前行参与 MIN 计算的必填 ROWS Frame；起止边界均包含。")
            RowsWindowFrame frame
    ) implements WindowFunctionItem {
    }

    @JsonClassDescription("窗口函数分支：计算当前行窗口 Frame 内来源字段的最大值。")

    record Max(
            @JsonPropertyDescription("来源字段名；必须存在于当前操作所引用的上游逻辑表 Schema 中。")
            String sourceColumnName,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName,
            @JsonPropertyDescription("当前行参与 MAX 计算的必填 ROWS Frame；起止边界均包含。")
            RowsWindowFrame frame
    ) implements WindowFunctionItem {
    }

    @JsonClassDescription("窗口函数分支：读取当前行窗口 Frame 内按排序出现的第一个来源字段值，可忽略 NULL。")

    record FirstValue(
            @JsonPropertyDescription("来源字段名；必须存在于当前操作所引用的上游逻辑表 Schema 中。")
            String sourceColumnName,
            @JsonPropertyDescription("是否跳过 Frame 中的 NULL 值；true 返回首个非 NULL 值，false 允许首值为 NULL。")
            boolean ignoreNulls,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName,
            @JsonPropertyDescription("查找 FIRST_VALUE 的必填 ROWS Frame；起止边界均包含。")
            RowsWindowFrame frame
    ) implements WindowFunctionItem {
    }

    @JsonClassDescription("窗口函数分支：读取当前行窗口 Frame 内按排序出现的最后一个来源字段值，可忽略 NULL。")

    record LastValue(
            @JsonPropertyDescription("来源字段名；必须存在于当前操作所引用的上游逻辑表 Schema 中。")
            String sourceColumnName,
            @JsonPropertyDescription("是否跳过 Frame 中的 NULL 值；true 返回末个非 NULL 值，false 允许末值为 NULL。")
            boolean ignoreNulls,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName,
            @JsonPropertyDescription("查找 LAST_VALUE 的必填 ROWS Frame；起止边界均包含。")
            RowsWindowFrame frame
    ) implements WindowFunctionItem {
    }
}
