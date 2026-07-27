package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class CanvasNodeOperatorRegistry {
    private final Map<CanvasNodeType, CanvasNodeOperator> operators;

    public CanvasNodeOperatorRegistry(Collection<? extends CanvasNodeOperator> operators) {
        Objects.requireNonNull(operators, "operators");
        Map<CanvasNodeType, CanvasNodeOperator> indexed = new EnumMap<>(CanvasNodeType.class);
        for (CanvasNodeOperator operator : operators) {
            Objects.requireNonNull(operator, "operator");
            CanvasNodeType nodeType = Objects.requireNonNull(operator.nodeType(), "operator.nodeType");
            Objects.requireNonNull(operator.category(), "operator.category");
            if (operator.supportedModes() == null || operator.supportedModes().isEmpty()) {
                throw new IllegalArgumentException("Canvas node operator supportedModes is empty: " + nodeType);
            }
            if (indexed.putIfAbsent(nodeType, operator) != null) {
                throw new IllegalArgumentException("Canvas node operator is duplicated: " + nodeType);
            }
        }
        this.operators = Map.copyOf(indexed);
    }

    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition node,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        Objects.requireNonNull(node, "node");
        return require(node.nodeType()).apply(node, inputs, context);
    }

    public CanvasNodeOperator require(CanvasNodeType nodeType) {
        CanvasNodeOperator operator = operators.get(nodeType);
        if (operator == null) {
            throw new IllegalArgumentException("Canvas node operator is not registered: " + nodeType);
        }
        return operator;
    }

    public boolean supports(CanvasNodeType nodeType, CanvasExecutionMode mode) {
        return require(nodeType).supportedModes().contains(mode);
    }
}
