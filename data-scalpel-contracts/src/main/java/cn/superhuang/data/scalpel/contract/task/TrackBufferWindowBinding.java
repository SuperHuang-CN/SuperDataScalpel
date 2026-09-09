package cn.superhuang.data.scalpel.contract.task;

/** Inclusive observation offsets, evaluated before optional gap/expression endpoint sharing. */
public record TrackBufferWindowBinding(
        String name,
        String sourceColumnName,
        Integer startOffset,
        Integer endOffset,
        TrackSummaryStatisticKind statistic
) { }
