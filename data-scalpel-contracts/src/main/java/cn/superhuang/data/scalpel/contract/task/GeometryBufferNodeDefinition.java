package cn.superhuang.data.scalpel.contract.task;

public record GeometryBufferNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        GeometryBufferConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.GEOMETRY_BUFFER;
    }
}
