package cn.superhuang.data.scalpel.contract.task;

public record SpatialAggregation(
        SpatialAggregationKind kind,
        String geometryColumnName,
        String outputColumnName
) {
}
