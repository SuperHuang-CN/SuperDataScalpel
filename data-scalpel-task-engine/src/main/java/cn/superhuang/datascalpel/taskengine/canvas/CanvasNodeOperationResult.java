package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CanvasNodeOperationResult(
        Map<String, SparkCanvasTable> propagatedTables,
        List<CanvasTableSchema> displayedOutputTables,
        CanvasPreparedOutput preparedOutput,
        CanvasPreparedKafkaOutput preparedKafkaOutput,
        CanvasPreparedFileOutput preparedFileOutput
) {
    public CanvasNodeOperationResult {
        propagatedTables = Collections.unmodifiableMap(new LinkedHashMap<>(propagatedTables));
        displayedOutputTables = List.copyOf(displayedOutputTables);
    }

    public static CanvasNodeOperationResult propagated(
            Map<String, SparkCanvasTable> tables,
            List<CanvasTableSchema> displayedOutputTables
    ) {
        return new CanvasNodeOperationResult(tables, displayedOutputTables, null, null, null);
    }

    public static CanvasNodeOperationResult invalid(List<CanvasTableSchema> displayedOutputTables) {
        return new CanvasNodeOperationResult(Map.of(), displayedOutputTables, null, null, null);
    }

    public static CanvasNodeOperationResult output(CanvasPreparedOutput preparedOutput) {
        return new CanvasNodeOperationResult(Map.of(), List.of(), preparedOutput, null, null);
    }

    public static CanvasNodeOperationResult kafkaOutput(CanvasPreparedKafkaOutput preparedOutput) {
        return new CanvasNodeOperationResult(Map.of(), List.of(), null, preparedOutput, null);
    }

    public static CanvasNodeOperationResult fileOutput(CanvasPreparedFileOutput preparedOutput) {
        return new CanvasNodeOperationResult(Map.of(), List.of(), null, null, preparedOutput);
    }

    public static CanvasNodeOperationResult outputOnly() {
        return output(null);
    }
}
