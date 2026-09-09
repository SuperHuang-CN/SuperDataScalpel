package cn.superhuang.data.scalpel.contract.task;

public record TrackReconstructNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        TrackReconstructConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.TRACK_RECONSTRUCT;
    }
}
