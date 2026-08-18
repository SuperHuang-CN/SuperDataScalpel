package cn.superhuang.data.scalpel.contract.task;

public record JdbcIncrementalInputNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        JdbcIncrementalInputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.JDBC_INCREMENTAL_INPUT;
    }
}
