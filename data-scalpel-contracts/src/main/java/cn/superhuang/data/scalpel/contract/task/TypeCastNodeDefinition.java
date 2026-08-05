package cn.superhuang.data.scalpel.contract.task;

public record TypeCastNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        TypeCastConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.TYPE_CAST;
    }
}
