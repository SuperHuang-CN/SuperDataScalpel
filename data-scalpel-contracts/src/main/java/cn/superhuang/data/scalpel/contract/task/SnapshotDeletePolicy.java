package cn.superhuang.data.scalpel.contract.task;

public record SnapshotDeletePolicy(
        SnapshotTargetOnlyAction action,
        Long maxDeleteRows,
        Double maxDeleteRatio
) {
}
