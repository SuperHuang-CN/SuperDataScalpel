package cn.superhuang.data.scalpel.contract.task;

public record SpatialOverlayNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        SpatialOverlayConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_OVERLAY;
    }
}
