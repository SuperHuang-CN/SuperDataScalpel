package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record StreamJoinConfiguration(
        String leftTableName,
        String rightTableName,
        String outputTableName,
        StreamJoinType joinType,
        List<JoinCondition> conditions,
        List<JoinOutputColumn> outputColumns
) {
    public StreamJoinConfiguration {
        conditions = conditions == null ? null : List.copyOf(conditions);
        outputColumns = outputColumns == null ? null : List.copyOf(outputColumns);
    }

    public StreamJoinConfiguration(
            String leftTableName,
            String rightTableName,
            String outputTableName,
            StreamJoinType joinType,
            List<JoinCondition> conditions
    ) {
        this(leftTableName, rightTableName, outputTableName, joinType, conditions, List.of());
    }
}
