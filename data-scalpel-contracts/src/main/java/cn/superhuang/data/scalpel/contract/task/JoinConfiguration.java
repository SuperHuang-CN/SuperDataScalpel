package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record JoinConfiguration(
        String leftTableName,
        String rightTableName,
        String outputTableName,
        JoinType joinType,
        List<JoinCondition> conditions
) {
}
