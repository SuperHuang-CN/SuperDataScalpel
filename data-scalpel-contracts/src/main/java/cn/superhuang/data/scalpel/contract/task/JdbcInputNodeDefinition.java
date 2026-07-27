package cn.superhuang.data.scalpel.contract.task;

public record JdbcInputNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        JdbcInputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.JDBC_INPUT;
    }
}
