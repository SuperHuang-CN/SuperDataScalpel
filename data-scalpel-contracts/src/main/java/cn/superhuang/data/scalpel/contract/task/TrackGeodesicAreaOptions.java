package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** Explicit area boundary sampling, independent of inactive line-path settings. */
@JsonClassDescription("EPSG:4326 XY 测地面轨迹的边界采样参数；控制 WGS84 边界离散粒度，不表示最大位置误差或平台自动选择的业务精度。")
public record TrackGeodesicAreaOptions(
        @JsonPropertyDescription("输出边界相邻采样点允许的最大测地距离，必须为有限正数。")
        Double maximumSegmentLength,
        @JsonPropertyDescription("maximumSegmentLength 的线性距离单位；不接受 SOURCE_CRS_UNIT。")
        SpatialDistanceUnit maximumSegmentLengthUnit
) { }
