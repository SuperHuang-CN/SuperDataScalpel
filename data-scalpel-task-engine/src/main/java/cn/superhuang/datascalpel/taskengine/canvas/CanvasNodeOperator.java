package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;

import java.util.Map;
import java.util.Set;

public interface CanvasNodeOperator {

    CanvasNodeType nodeType();

    CanvasNodeCategory category();

    Set<CanvasExecutionMode> supportedModes();

    CanvasNodeOperationResult apply(
            CanvasNodeDefinition node,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    );
}
