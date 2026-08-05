package cn.superhuang.data.scalpel.contract.task;

public record FilterNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        FilterConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.FILTER;
    }
}
