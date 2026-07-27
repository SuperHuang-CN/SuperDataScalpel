package cn.superhuang.datascalpel.taskengine.compiler.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record NodeCompileOutput(
        Map<String, SparkCanvasTable> propagatedTables,
        List<CanvasTableSchema> displayedOutputTables
) {
    NodeCompileOutput {
        propagatedTables = Collections.unmodifiableMap(new LinkedHashMap<>(propagatedTables));
        displayedOutputTables = List.copyOf(displayedOutputTables);
    }

    static NodeCompileOutput invalid(List<CanvasTableSchema> displayedOutputTables) {
        return new NodeCompileOutput(Map.of(), displayedOutputTables);
    }

    static NodeCompileOutput outputOnly() {
        return new NodeCompileOutput(Map.of(), List.of());
    }
}
