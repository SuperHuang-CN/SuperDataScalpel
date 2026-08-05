package cn.superhuang.data.scalpel.contract.task;

public record UnionNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        UnionConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.UNION;
    }
}
