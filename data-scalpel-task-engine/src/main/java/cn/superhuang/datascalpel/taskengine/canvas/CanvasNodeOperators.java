package cn.superhuang.datascalpel.taskengine.canvas;

import java.util.List;

/**
 * Built-in Canvas node operators shared by preflight compilation and runtime execution.
 */
public final class CanvasNodeOperators {
    private static final CanvasNodeOperatorRegistry BUILT_IN_REGISTRY =
            new CanvasNodeOperatorRegistry(List.of(
                    new ModelInputNodeOperator(),
                    new JdbcInputNodeOperator(),
                    new FileDatasetInputNodeOperator(),
                    new HttpApiInputNodeOperator(),
                    new KafkaInputNodeOperator(),
                    new JoinNodeOperator(),
                    new StreamJoinNodeOperator(),
                    new RenameNodeOperator(),
                    new ModelOutputNodeOperator(),
                    new JdbcOutputNodeOperator(),
                    new KafkaOutputNodeOperator()
            ));

    private CanvasNodeOperators() {
    }

    public static CanvasNodeOperatorRegistry builtInRegistry() {
        return BUILT_IN_REGISTRY;
    }
}
