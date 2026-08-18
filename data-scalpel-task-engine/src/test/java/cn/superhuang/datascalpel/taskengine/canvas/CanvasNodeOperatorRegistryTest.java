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
        assertBatchAndStreaming(registry, CanvasNodeType.JDBC_QUERY_INPUT);
        assertTrue(registry.supports(CanvasNodeType.FILE_DATASET_INPUT, CanvasExecutionMode.BATCH));
        assertFalse(registry.supports(CanvasNodeType.FILE_DATASET_INPUT, CanvasExecutionMode.STREAMING));
        assertTrue(registry.supports(CanvasNodeType.KAFKA_INPUT, CanvasExecutionMode.STREAMING));
        assertFalse(registry.supports(CanvasNodeType.KAFKA_INPUT, CanvasExecutionMode.BATCH));
        assertTrue(registry.supports(CanvasNodeType.JOIN, CanvasExecutionMode.BATCH));
        assertFalse(registry.supports(CanvasNodeType.JOIN, CanvasExecutionMode.STREAMING));
        assertBatchAndStreaming(registry, CanvasNodeType.GEOMETRY_CONSTRUCT);
        assertBatchOnly(registry, CanvasNodeType.SPATIAL_TRANSFORM);
        assertBatchAndStreaming(registry, CanvasNodeType.GEOMETRY_VALIDATE);
        assertBatchAndStreaming(registry, CanvasNodeType.GEOMETRY_REPAIR);
        assertBatchAndStreaming(registry, CanvasNodeType.GEOMETRY_BUFFER);
        assertBatchAndStreaming(registry, CanvasNodeType.GEOMETRY_EXPLODE);
        assertBatchAndStreaming(registry, CanvasNodeType.SPATIAL_MEASURE);
        assertBatchAndStreaming(registry, CanvasNodeType.GEOMETRY_SERIALIZE);
        assertBatchOnly(registry, CanvasNodeType.SPATIAL_CLIP);
        assertBatchOnly(registry, CanvasNodeType.SPATIAL_AGGREGATE);
        assertBatchOnly(registry, CanvasNodeType.SPATIAL_JOIN);
        assertTrue(registry.supports(CanvasNodeType.STREAM_JOIN, CanvasExecutionMode.STREAMING));
        assertFalse(registry.supports(CanvasNodeType.STREAM_JOIN, CanvasExecutionMode.BATCH));
        assertBatchAndStreaming(registry, CanvasNodeType.RENAME);
        assertBatchAndStreaming(registry, CanvasNodeType.FILTER);
        assertBatchAndStreaming(registry, CanvasNodeType.SELECT_COLUMNS);
        assertBatchAndStreaming(registry, CanvasNodeType.DERIVE_COLUMNS);
        assertBatchAndStreaming(registry, CanvasNodeType.TYPE_CAST);
        assertBatchOnly(registry, CanvasNodeType.AGGREGATE);
        assertBatchAndStreaming(registry, CanvasNodeType.UNION);
        assertBatchOnly(registry, CanvasNodeType.DEDUPLICATE);
        assertBatchAndStreaming(registry, CanvasNodeType.NULL_HANDLING);
        assertBatchAndStreaming(registry, CanvasNodeType.VALUE_MAPPING);
        assertBatchAndStreaming(registry, CanvasNodeType.MASK_FIELDS);
        assertBatchAndStreaming(registry, CanvasNodeType.JSON_EXTRACT);
        assertBatchAndStreaming(registry, CanvasNodeType.MODEL_OUTPUT);
        assertBatchOnly(registry, CanvasNodeType.WINDOW);
        assertBatchOnly(registry, CanvasNodeType.TOP_N);
    }

    private static void assertBatchAndStreaming(
            CanvasNodeOperatorRegistry registry,
            CanvasNodeType nodeType
    ) {
        assertTrue(registry.supports(nodeType, CanvasExecutionMode.BATCH));
        assertTrue(registry.supports(nodeType, CanvasExecutionMode.STREAMING));
    }

    private static void assertBatchOnly(
            CanvasNodeOperatorRegistry registry,
            CanvasNodeType nodeType
    ) {
        assertTrue(registry.supports(nodeType, CanvasExecutionMode.BATCH));
        assertFalse(registry.supports(nodeType, CanvasExecutionMode.STREAMING));
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
