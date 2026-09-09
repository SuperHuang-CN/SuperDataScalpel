package cn.superhuang.data.scalpel.contract.task;

public record SpatialBinStatistic(
        String statisticId,
        SpatialBinStatisticKind kind,
        String sourceColumnName,
        String outputColumnName
) {
}
