package cn.superhuang.data.scalpel.contract.task;

public record SpatialNearestNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        SpatialNearestConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_NEAREST;
    }
}
