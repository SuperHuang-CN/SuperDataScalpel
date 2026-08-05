package cn.superhuang.data.scalpel.contract.task;

public record DeriveColumnsNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        DeriveColumnsConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.DERIVE_COLUMNS;
    }
}
