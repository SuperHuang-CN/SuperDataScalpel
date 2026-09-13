package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("轨迹窗口统计种类。DISTANCE/DURATION/SPEED/ACCELERATION/ELEV_CHANGE/SLOPE/IDLING/BEARING 是当前段或当前点值；TOT/MIN/MAX/AVG 是当前观测窗口内的汇总。AVG_SPEED 为可计算速度段的总距离除以总时长；坡度为高差/水平距离且不乘 100；TOT_ELEV_CHANGE 保留正负；PCT_IDLE_TIME 为可分类段中静止时长百分比。缺失测量不当作 0，全部缺失时结果为 NULL。")
public enum TrackMotionStatistic {
    DISTANCE(TrackMotionStatisticGroup.DISTANCE),
    TOT_DISTANCE(TrackMotionStatisticGroup.DISTANCE),
    MIN_DISTANCE(TrackMotionStatisticGroup.DISTANCE),
    MAX_DISTANCE(TrackMotionStatisticGroup.DISTANCE),
    AVG_DISTANCE(TrackMotionStatisticGroup.DISTANCE),
    DURATION(TrackMotionStatisticGroup.DURATION),
    TOT_DURATION(TrackMotionStatisticGroup.DURATION),
    MIN_DURATION(TrackMotionStatisticGroup.DURATION),
    MAX_DURATION(TrackMotionStatisticGroup.DURATION),
    AVG_DURATION(TrackMotionStatisticGroup.DURATION),
    SPEED(TrackMotionStatisticGroup.SPEED),
    MIN_SPEED(TrackMotionStatisticGroup.SPEED),
    MAX_SPEED(TrackMotionStatisticGroup.SPEED),
    AVG_SPEED(TrackMotionStatisticGroup.SPEED),
    ACCELERATION(TrackMotionStatisticGroup.ACCELERATION),
    MIN_ACCELERATION(TrackMotionStatisticGroup.ACCELERATION),
    MAX_ACCELERATION(TrackMotionStatisticGroup.ACCELERATION),
    ELEVATION(TrackMotionStatisticGroup.ELEVATION),
    ELEV_CHANGE(TrackMotionStatisticGroup.ELEVATION),
    TOT_ELEV_CHANGE(TrackMotionStatisticGroup.ELEVATION),
    MIN_ELEVATION(TrackMotionStatisticGroup.ELEVATION),
    MAX_ELEVATION(TrackMotionStatisticGroup.ELEVATION),
    AVG_ELEVATION(TrackMotionStatisticGroup.ELEVATION),
    SLOPE(TrackMotionStatisticGroup.SLOPE),
    MIN_SLOPE(TrackMotionStatisticGroup.SLOPE),
    MAX_SLOPE(TrackMotionStatisticGroup.SLOPE),
    AVG_SLOPE(TrackMotionStatisticGroup.SLOPE),
    IDLING(TrackMotionStatisticGroup.IDLE),
    TOT_IDLE_TIME(TrackMotionStatisticGroup.IDLE),
    PCT_IDLE_TIME(TrackMotionStatisticGroup.IDLE),
    BEARING(TrackMotionStatisticGroup.BEARING);

    private final TrackMotionStatisticGroup group;
    TrackMotionStatistic(TrackMotionStatisticGroup group) { this.group = group; }
    public TrackMotionStatisticGroup group() { return group; }
}
