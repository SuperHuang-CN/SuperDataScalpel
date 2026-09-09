package cn.superhuang.data.scalpel.contract.task;

public record TrackSummaryStatistic(
        String statisticId,
        TrackSummaryStatisticKind kind,
        String sourceColumnName,
        String outputColumnName
) {
}
