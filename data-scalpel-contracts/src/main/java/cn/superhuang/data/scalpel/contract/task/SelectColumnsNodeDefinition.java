package cn.superhuang.data.scalpel.contract.task;

public record SelectColumnsNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        SelectColumnsConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SELECT_COLUMNS;
    }
}
