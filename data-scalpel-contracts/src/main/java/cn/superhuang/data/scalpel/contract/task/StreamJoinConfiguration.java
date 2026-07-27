package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record StreamJoinConfiguration(
        String leftTableName,
        String rightTableName,
        String outputTableName,
        StreamJoinType joinType,
        List<JoinCondition> conditions
) {
}
