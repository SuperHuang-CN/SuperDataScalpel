package cn.superhuang.data.scalpel.contract.task;

public record SpatialServiceInputNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        SpatialServiceInputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_SERVICE_INPUT;
    }
}
