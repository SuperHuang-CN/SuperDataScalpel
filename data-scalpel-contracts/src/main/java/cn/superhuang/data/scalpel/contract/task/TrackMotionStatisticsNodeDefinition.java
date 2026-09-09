package cn.superhuang.data.scalpel.contract.task;

public record TrackMotionStatisticsNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        TrackMotionStatisticsConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.TRACK_MOTION_STATISTICS;
    }
}
