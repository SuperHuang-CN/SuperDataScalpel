package cn.superhuang.data.scalpel.contract.task;

public record SpatialNearestMatching(
        SpatialNearestMatchSemantics semantics,
        String sourceIdColumnName,
        SpatialNearestConnectionLines connectionLines
) { }
