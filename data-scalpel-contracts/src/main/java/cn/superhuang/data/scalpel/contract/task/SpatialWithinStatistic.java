package cn.superhuang.data.scalpel.contract.task;

public record SpatialWithinStatistic(
        String statisticId,
        SpatialWithinStatisticKind kind,
        String sourceColumnName,
        String outputColumnName,
        SpatialWithinValueTreatment valueTreatment,
        SpatialWithinWeighting weighting
) {
    public SpatialWithinStatistic(String statisticId, SpatialWithinStatisticKind kind,
                                  String sourceColumnName, String outputColumnName) {
        this(statisticId, kind, sourceColumnName, outputColumnName, null, null);
    }

    public boolean apportionsTotal() {
        return valueTreatment == SpatialWithinValueTreatment.APPORTION_TOTAL;
    }

    public boolean usesGeographicWeight() {
        return weighting == SpatialWithinWeighting.INTERSECTION_FRACTION;
    }

    public boolean requiresWeightedDispersionVersion() {
        return usesGeographicWeight() && (kind == SpatialWithinStatisticKind.VARIANCE
                || kind == SpatialWithinStatisticKind.STDDEV);
    }

    public boolean requiresExplicitStatisticsVersion() {
        return valueTreatment != null || weighting != null
                || kind == SpatialWithinStatisticKind.COUNT_FIELD || kind == SpatialWithinStatisticKind.ANY;
    }
}
