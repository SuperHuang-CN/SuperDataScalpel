package cn.superhuang.data.scalpel.contract.task;

public record HttpApiInputNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        HttpApiInputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.HTTP_API_INPUT;
    }
}
