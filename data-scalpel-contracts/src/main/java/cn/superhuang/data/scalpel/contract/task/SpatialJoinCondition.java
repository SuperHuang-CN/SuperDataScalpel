package cn.superhuang.data.scalpel.contract.task;

public record SpatialJoinCondition(
        String leftGeometryColumnName,
        SpatialPredicate predicate,
        String rightGeometryColumnName
) {
}
