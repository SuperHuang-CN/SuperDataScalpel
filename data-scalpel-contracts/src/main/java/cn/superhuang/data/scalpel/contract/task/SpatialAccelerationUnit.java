package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("加速度输出单位：米每二次方秒或国际英尺每二次方秒。")
public enum SpatialAccelerationUnit {
    METERS_PER_SECOND_SQUARED,
    FEET_PER_SECOND_SQUARED
}
