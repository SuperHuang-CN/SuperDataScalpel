package cn.superhuang.data.scalpel.contract.task;

public record SpatialClipNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        SpatialClipConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_CLIP;
    }
}
