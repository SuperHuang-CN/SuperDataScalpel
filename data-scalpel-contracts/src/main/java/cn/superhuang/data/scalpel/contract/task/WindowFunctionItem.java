package cn.superhuang.data.scalpel.contract.task;

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

    record RowNumber(String outputColumnName) implements WindowFunctionItem {
    }

    record Rank(String outputColumnName) implements WindowFunctionItem {
    }

    record DenseRank(String outputColumnName) implements WindowFunctionItem {
    }

    record Lag(
            String sourceColumnName,
            int offset,
            CanvasLiteral defaultValue,
            String outputColumnName
    ) implements WindowFunctionItem {
    }

    record Lead(
            String sourceColumnName,
            int offset,
            CanvasLiteral defaultValue,
            String outputColumnName
    ) implements WindowFunctionItem {
    }

    record Count(
            String sourceColumnName,
            String outputColumnName,
            RowsWindowFrame frame
    ) implements WindowFunctionItem {
    }

    record Sum(
            String sourceColumnName,
            String outputColumnName,
            RowsWindowFrame frame
    ) implements WindowFunctionItem {
    }

    record Avg(
            String sourceColumnName,
            String outputColumnName,
            RowsWindowFrame frame
    ) implements WindowFunctionItem {
    }

    record Min(
            String sourceColumnName,
            String outputColumnName,
            RowsWindowFrame frame
    ) implements WindowFunctionItem {
    }

    record Max(
            String sourceColumnName,
            String outputColumnName,
            RowsWindowFrame frame
    ) implements WindowFunctionItem {
    }

    record FirstValue(
            String sourceColumnName,
            boolean ignoreNulls,
            String outputColumnName,
            RowsWindowFrame frame
    ) implements WindowFunctionItem {
    }

    record LastValue(
            String sourceColumnName,
            boolean ignoreNulls,
            String outputColumnName,
            RowsWindowFrame frame
    ) implements WindowFunctionItem {
    }
}
