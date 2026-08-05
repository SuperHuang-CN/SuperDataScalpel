package cn.superhuang.data.scalpel.contract.task;

public record SpatialJoinNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        SpatialJoinConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_JOIN;
    }
}
