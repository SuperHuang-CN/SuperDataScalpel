package cn.superhuang.data.scalpel.contract.task;

/** Explicit area boundary sampling, independent of inactive line-path settings. */
public record TrackGeodesicAreaOptions(
        Double maximumSegmentLength,
        SpatialDistanceUnit maximumSegmentLengthUnit
) { }
