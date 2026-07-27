package cn.superhuang.data.scalpel.contract.task;

public record ModelOutputNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        ModelOutputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.MODEL_OUTPUT;
    }
}
