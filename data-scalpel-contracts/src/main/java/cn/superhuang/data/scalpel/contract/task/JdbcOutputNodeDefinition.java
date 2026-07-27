package cn.superhuang.data.scalpel.contract.task;

public record JdbcOutputNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        JdbcOutputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.JDBC_OUTPUT;
    }
}
