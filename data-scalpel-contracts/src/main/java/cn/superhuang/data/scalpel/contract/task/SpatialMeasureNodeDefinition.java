package cn.superhuang.data.scalpel.contract.task;

public record SpatialMeasureNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        SpatialMeasureConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_MEASURE;
    }
}
