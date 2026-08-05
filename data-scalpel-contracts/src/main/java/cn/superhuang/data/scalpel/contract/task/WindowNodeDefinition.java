package cn.superhuang.data.scalpel.contract.task;

public record WindowNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        WindowConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.WINDOW;
    }
}
