package cn.superhuang.data.scalpel.contract.task;

public record SpatialSummarizeWithinNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        SpatialSummarizeWithinConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_SUMMARIZE_WITHIN;
    }
}
