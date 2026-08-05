package cn.superhuang.data.scalpel.contract.task;

public record SpatialTransformNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        SpatialTransformConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_TRANSFORM;
    }
}
