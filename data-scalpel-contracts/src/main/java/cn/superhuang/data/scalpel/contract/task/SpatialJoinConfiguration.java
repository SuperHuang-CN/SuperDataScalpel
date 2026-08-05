package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record SpatialJoinConfiguration(
        String leftTableName,
        String rightTableName,
        String outputTableName,
        JoinType joinType,
        List<SpatialJoinCondition> conditions
) {
    public static final int MAX_CONDITIONS = 8;

    public SpatialJoinConfiguration {
        conditions = conditions == null ? null : List.copyOf(conditions);
    }
}
