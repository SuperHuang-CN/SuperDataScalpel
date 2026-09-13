package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("速度输出单位：米/秒、千米/小时、国际英尺/秒、国际英里/小时或国际海里/小时（节）。")
public enum SpatialSpeedUnit {
    METERS_PER_SECOND,
    KILOMETERS_PER_HOUR,
    FEET_PER_SECOND,
    MILES_PER_HOUR,
    KNOTS
}
