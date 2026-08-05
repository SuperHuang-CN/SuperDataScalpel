package cn.superhuang.data.scalpel.contract.task;

public record TopNNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        TopNConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.TOP_N;
    }
}
