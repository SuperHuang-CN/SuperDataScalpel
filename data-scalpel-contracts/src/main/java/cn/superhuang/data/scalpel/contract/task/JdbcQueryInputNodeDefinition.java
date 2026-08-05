package cn.superhuang.data.scalpel.contract.task;

public record JdbcQueryInputNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        JdbcQueryInputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.JDBC_QUERY_INPUT;
    }
}
