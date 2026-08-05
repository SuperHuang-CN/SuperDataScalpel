package cn.superhuang.data.scalpel.contract.task;

public record ValueMappingNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        ValueMappingConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.VALUE_MAPPING;
    }
}
