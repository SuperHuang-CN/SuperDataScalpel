package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record JoinConfiguration(
        String leftTableName,
        String rightTableName,
        String outputTableName,
        JoinType joinType,
        List<JoinCondition> conditions,
        List<JoinOutputColumn> outputColumns
) {
    public JoinConfiguration {
        conditions = conditions == null ? null : List.copyOf(conditions);
        outputColumns = outputColumns == null ? null : List.copyOf(outputColumns);
    }

    public JoinConfiguration(
            String leftTableName,
            String rightTableName,
            String outputTableName,
            JoinType joinType,
            List<JoinCondition> conditions
    ) {
        this(leftTableName, rightTableName, outputTableName, joinType, conditions, List.of());
    }
}
