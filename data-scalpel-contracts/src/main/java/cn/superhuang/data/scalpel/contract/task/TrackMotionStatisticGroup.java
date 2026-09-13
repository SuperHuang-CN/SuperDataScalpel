package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("轨迹运动统计的固定分组：距离、时长、速度、加速度、高程、坡度、静止和方位角。配置任一组时必须同时配置 TrackMotionStatistic 中属于该组的全部成员。")
public enum TrackMotionStatisticGroup { DISTANCE, DURATION, SPEED, ACCELERATION, ELEVATION, SLOPE, IDLE, BEARING }
