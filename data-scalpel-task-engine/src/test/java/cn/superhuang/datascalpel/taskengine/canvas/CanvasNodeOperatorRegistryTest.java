package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasNodeOperatorRegistryTest {

    @Test
    void indexesOperatorsByNodeType() {
        CanvasNodeOperator join = operator(CanvasNodeType.JOIN);
        CanvasNodeOperatorRegistry registry = new CanvasNodeOperatorRegistry(List.of(join));

        assertSame(join, registry.require(CanvasNodeType.JOIN));
        assertThrows(IllegalArgumentException.class, () -> registry.require(CanvasNodeType.JDBC_INPUT));
    }

    @Test
    void rejectsDuplicateNodeTypes() {
        assertThrows(IllegalArgumentException.class, () -> new CanvasNodeOperatorRegistry(List.of(
                operator(CanvasNodeType.JOIN),
                operator(CanvasNodeType.JOIN)
        )));
    }

    @Test
    void builtInRegistryContainsEverySupportedNodeTypeAndIsShared() {
        CanvasNodeOperatorRegistry registry = CanvasNodeOperators.builtInRegistry();

        for (CanvasNodeType nodeType : CanvasNodeType.values()) {
            CanvasNodeOperator operator = registry.require(nodeType);
            assertSame(operator, CanvasNodeOperators.builtInRegistry().require(nodeType));
            assertFalse(operator.supportedModes().isEmpty());
        }
        assertTrue(registry.supports(CanvasNodeType.JDBC_INPUT, CanvasExecutionMode.BATCH));
        assertTrue(registry.supports(CanvasNodeType.JDBC_INPUT, CanvasExecutionMode.STREAMING));
        assertTrue(registry.supports(CanvasNodeType.FILE_DATASET_INPUT, CanvasExecutionMode.BATCH));
        assertFalse(registry.supports(CanvasNodeType.FILE_DATASET_INPUT, CanvasExecutionMode.STREAMING));
        assertTrue(registry.supports(CanvasNodeType.KAFKA_INPUT, CanvasExecutionMode.STREAMING));
        assertFalse(registry.supports(CanvasNodeType.KAFKA_INPUT, CanvasExecutionMode.BATCH));
        assertTrue(registry.supports(CanvasNodeType.JOIN, CanvasExecutionMode.BATCH));
        assertFalse(registry.supports(CanvasNodeType.JOIN, CanvasExecutionMode.STREAMING));
        assertTrue(registry.supports(CanvasNodeType.STREAM_JOIN, CanvasExecutionMode.STREAMING));
        assertFalse(registry.supports(CanvasNodeType.STREAM_JOIN, CanvasExecutionMode.BATCH));
    }

    private static CanvasNodeOperator operator(CanvasNodeType nodeType) {
        return new CanvasNodeOperator() {
            @Override
            public CanvasNodeType nodeType() {
                return nodeType;
            }

            @Override
            public CanvasNodeCategory category() {
                return CanvasNodeCategory.PROCESSOR;
            }

            @Override
            public Set<CanvasExecutionMode> supportedModes() {
                return Set.of(CanvasExecutionMode.BATCH);
            }

            @Override
            public CanvasNodeOperationResult apply(
                    CanvasNodeDefinition node,
                    Map<String, SparkCanvasTable> inputs,
                    CanvasNodeOperationContext context
            ) {
                return CanvasNodeOperationResult.outputOnly();
            }
        };
    }
}
