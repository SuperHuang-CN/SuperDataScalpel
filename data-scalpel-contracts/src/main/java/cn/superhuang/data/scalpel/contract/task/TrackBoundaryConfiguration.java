package cn.superhuang.data.scalpel.contract.task;

public record TrackBoundaryConfiguration(
        Double maximumTimeGap,
        SpatialDurationUnit maximumTimeGapUnit,
        Double maximumDistanceGap,
        SpatialDistanceUnit maximumDistanceGapUnit,
        TrackFixedTimeBoundary fixedTimeBoundary
) {
    public TrackBoundaryConfiguration(Double maximumTimeGap, SpatialDurationUnit maximumTimeGapUnit,
                                      Double maximumDistanceGap, SpatialDistanceUnit maximumDistanceGapUnit) {
        this(maximumTimeGap, maximumTimeGapUnit, maximumDistanceGap, maximumDistanceGapUnit, null);
    }
}
