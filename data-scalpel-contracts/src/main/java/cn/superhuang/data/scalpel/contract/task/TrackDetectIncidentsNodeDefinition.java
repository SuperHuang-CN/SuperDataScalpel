package cn.superhuang.data.scalpel.contract.task;

public record TrackDetectIncidentsNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        TrackDetectIncidentsConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.TRACK_DETECT_INCIDENTS;
    }
}
