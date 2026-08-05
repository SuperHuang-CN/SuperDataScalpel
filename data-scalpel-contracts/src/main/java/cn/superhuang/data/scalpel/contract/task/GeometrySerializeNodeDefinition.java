package cn.superhuang.data.scalpel.contract.task;

public record GeometrySerializeNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        GeometrySerializeConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.GEOMETRY_SERIALIZE;
    }
}
