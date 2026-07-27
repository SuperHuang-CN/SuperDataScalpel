package cn.superhuang.datascalpel.taskengine.compiler.canvas;

import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperationContext;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperationResult;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperatorRegistry;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperators;
import cn.superhuang.datascalpel.taskengine.canvas.SchemaOnlyCanvasNodeDataAccess;
import cn.superhuang.datascalpel.taskengine.compiler.CompilationCancelledException;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.SparkSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CanvasTaskCompiler {
    private final CanvasNodeOperatorRegistry nodeOperators = CanvasNodeOperators.builtInRegistry();

    public CanvasCompilation compile(
            CanvasDefinition definition,
            MetadataIndex metadataIndex,
            SparkSession sparkSession,
            AtomicBoolean cancelled
    ) {
        return compile(definition, CanvasExecutionMode.BATCH, metadataIndex, sparkSession, cancelled);
    }

    public CanvasCompilation compile(
            CanvasDefinition definition,
            CanvasExecutionMode executionMode,
            MetadataIndex metadataIndex,
            SparkSession sparkSession,
        AtomicBoolean cancelled
    ) {
        CanvasGraphPlan plan = CanvasGraphPlan.create(definition, executionMode);
        List<Map<String, SparkCanvasTable>> propagated = new ArrayList<>();
        for (int index = 0; index < plan.entries().size(); index++) propagated.add(Map.of());

        for (int entryIndex : plan.topologicalOrder()) {
            checkCancelled(cancelled);
            CanvasGraphPlan.Entry entry = plan.entries().get(entryIndex);
            MutableNodeCompilation result = entry.result();

            Map<String, SparkCanvasTable> inputs = mergeInputs(entryIndex, plan, propagated, result);
            result.inputTables(inputs.values().stream().map(SparkCanvasTable::schema).toList());
            if (result.hasErrors()) continue;

            try {
                NodeCompileOutput output = compileNode(
                        entry.node(), inputs, metadataIndex, sparkSession, result);
                result.outputTables(output.displayedOutputTables());
                if (!result.hasErrors()) propagated.set(entryIndex, output.propagatedTables());
            } catch (Exception exception) {
                result.error("SPARK_ANALYSIS_ERROR", safeMessage(exception), "configuration");
            }
        }

        checkCancelled(cancelled);
        return CanvasCompilation.of(
                plan.canvasIssues(),
                plan.entries().stream().map(entry -> entry.result().result()).toList()
        );
    }

    private NodeCompileOutput compileNode(
            CanvasNodeDefinition node,
            Map<String, SparkCanvasTable> inputs,
            MetadataIndex metadataIndex,
            SparkSession sparkSession,
            MutableNodeCompilation result
    ) {
        CanvasNodeOperationResult output = nodeOperators.apply(
                node,
                inputs,
                new CanvasNodeOperationContext(
                        sparkSession,
                        metadataIndex,
                        result,
                        new SchemaOnlyCanvasNodeDataAccess(sparkSession)
                )
        );
        return new NodeCompileOutput(output.propagatedTables(), output.displayedOutputTables());
    }

    private static Map<String, SparkCanvasTable> mergeInputs(
            int entryIndex,
            CanvasGraphPlan plan,
            List<Map<String, SparkCanvasTable>> propagated,
            MutableNodeCompilation result
    ) {
        Map<String, SparkCanvasTable> merged = new LinkedHashMap<>();
        for (int predecessorIndex : plan.predecessorsOf(entryIndex)) {
            CanvasGraphPlan.Entry predecessor = plan.entries().get(predecessorIndex);
            if (predecessor.result().hasErrors()) {
                result.error("UPSTREAM_INVALID", "上游节点存在错误：" + predecessor.node().name(), "edges");
                continue;
            }
            for (Map.Entry<String, SparkCanvasTable> table : propagated.get(predecessorIndex).entrySet()) {
                if (merged.putIfAbsent(table.getKey(), table.getValue()) != null) {
                    result.error("DUPLICATE_TABLE_NAME", "上游数据包含同名表：" + table.getKey(), "edges");
                }
            }
        }
        return merged;
    }

    private static void checkCancelled(AtomicBoolean cancelled) {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new CompilationCancelledException();
        }
    }

    private static String safeMessage(Exception exception) {
        return "Spark Schema 分析失败";
    }
}
