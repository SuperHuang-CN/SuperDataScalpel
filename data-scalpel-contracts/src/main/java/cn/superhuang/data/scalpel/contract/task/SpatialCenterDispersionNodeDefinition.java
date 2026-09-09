package cn.superhuang.data.scalpel.contract.task;

public record SpatialCenterDispersionNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        SpatialCenterDispersionConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_CENTER_DISPERSION;
    }
}
