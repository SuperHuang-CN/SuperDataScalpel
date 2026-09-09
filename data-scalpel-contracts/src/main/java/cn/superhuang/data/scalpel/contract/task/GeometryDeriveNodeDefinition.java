package cn.superhuang.data.scalpel.contract.task;

public record GeometryDeriveNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        GeometryDeriveConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.GEOMETRY_DERIVE;
    }
}
