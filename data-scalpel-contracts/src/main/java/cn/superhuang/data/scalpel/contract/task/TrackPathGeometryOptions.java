package cn.superhuang.data.scalpel.contract.task;

public record TrackPathGeometryOptions(
        TrackPathGeometryMode mode,
        Double maximumGeodesicSegmentLength,
        SpatialDistanceUnit maximumGeodesicSegmentLengthUnit
) { }
