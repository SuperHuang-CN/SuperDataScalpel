package cn.superhuang.data.scalpel.contract.task;

public record SpatialAggregateNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        SpatialAggregateConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_AGGREGATE;
    }
}
