package cn.superhuang.data.scalpel.contract.task;

public record RenameNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        RenameConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.RENAME;
    }
}
