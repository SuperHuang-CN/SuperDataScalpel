package cn.superhuang.data.scalpel.contract.task;

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
