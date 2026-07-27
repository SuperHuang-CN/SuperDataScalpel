package cn.superhuang.data.scalpel.contract.task;

public record JoinCondition(
        String leftColumnName,
        JoinOperator operator,
        String rightColumnName
) {
}
