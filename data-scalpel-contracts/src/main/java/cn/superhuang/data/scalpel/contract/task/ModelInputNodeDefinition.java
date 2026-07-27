package cn.superhuang.data.scalpel.contract.task;

public record ModelInputNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        ModelInputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.MODEL_INPUT;
    }
}
