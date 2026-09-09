package cn.superhuang.data.scalpel.contract.task;

public record TrackFindDwellNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        TrackFindDwellConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.TRACK_FIND_DWELL;
    }
}
