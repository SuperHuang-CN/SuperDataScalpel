package cn.superhuang.data.scalpel.contract.task;

public record TdEngineTmqInputNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        TdEngineTmqInputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.TDENGINE_TMQ_INPUT;
    }
}
