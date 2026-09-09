package cn.superhuang.data.scalpel.contract.task;

public record SpatialBinAggregateNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        SpatialBinAggregateConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_BIN_AGGREGATE;
    }
}
