package cn.superhuang.data.scalpel.contract.task;

public record MaskFieldsNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        MaskFieldsConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.MASK_FIELDS;
    }
}
