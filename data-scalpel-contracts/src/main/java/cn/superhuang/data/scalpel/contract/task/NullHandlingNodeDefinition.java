package cn.superhuang.data.scalpel.contract.task;

public record NullHandlingNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        NullHandlingConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.NULL_HANDLING;
    }
}
