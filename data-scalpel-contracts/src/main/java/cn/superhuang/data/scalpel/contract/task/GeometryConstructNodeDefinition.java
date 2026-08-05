package cn.superhuang.data.scalpel.contract.task;

public record GeometryConstructNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        GeometryConstructConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.GEOMETRY_CONSTRUCT;
    }
}
