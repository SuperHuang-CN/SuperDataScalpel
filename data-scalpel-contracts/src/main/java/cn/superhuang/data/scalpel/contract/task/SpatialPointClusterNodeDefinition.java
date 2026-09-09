package cn.superhuang.data.scalpel.contract.task;

public record SpatialPointClusterNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        SpatialPointClusterConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_POINT_CLUSTER;
    }
}
