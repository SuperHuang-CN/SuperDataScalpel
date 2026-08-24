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
        List<CanvasLineageOutputCandidate> lineageOutputCandidates,
        List<CanvasPreparedOutput> preparedOutputs,
        CanvasPreparedSnapshotSyncOutput preparedSnapshotSyncOutput,
        List<CanvasPreparedKafkaOutput> preparedKafkaOutputs,
        List<CanvasPreparedFileOutput> preparedFileOutputs
) {
    public CanvasNodeOperationResult {
        propagatedTables = Collections.unmodifiableMap(new LinkedHashMap<>(propagatedTables));
        displayedOutputTables = List.copyOf(displayedOutputTables);
        lineageOutputCandidates = lineageOutputCandidates == null ? List.of() : List.copyOf(lineageOutputCandidates);
        preparedOutputs = preparedOutputs == null ? List.of() : List.copyOf(preparedOutputs);
        preparedKafkaOutputs = preparedKafkaOutputs == null ? List.of() : List.copyOf(preparedKafkaOutputs);
        preparedFileOutputs = preparedFileOutputs == null ? List.of() : List.copyOf(preparedFileOutputs);
    }

    public static CanvasNodeOperationResult propagated(
            Map<String, SparkCanvasTable> tables,
            List<CanvasTableSchema> displayedOutputTables
    ) {
        return new CanvasNodeOperationResult(tables, displayedOutputTables, List.of(), List.of(), null, List.of(), List.of());
    }

    public static CanvasNodeOperationResult invalid(List<CanvasTableSchema> displayedOutputTables) {
        return new CanvasNodeOperationResult(Map.of(), displayedOutputTables, List.of(), List.of(), null, List.of(), List.of());
    }

    public static CanvasNodeOperationResult output(CanvasPreparedOutput preparedOutput) {
        return new CanvasNodeOperationResult(Map.of(), List.of(), List.of(),
                preparedOutput == null ? List.of() : List.of(preparedOutput), null, List.of(), List.of());
    }

    public static CanvasNodeOperationResult output(
            CanvasPreparedOutput preparedOutput,
        CanvasLineageOutputCandidate lineageOutputCandidate
    ) {
        return new CanvasNodeOperationResult(
                Map.of(), List.of(), singleOrEmpty(lineageOutputCandidate), singleOrEmpty(preparedOutput), null, List.of(), List.of());
    }

    public static CanvasNodeOperationResult snapshotSyncOutput(
            CanvasPreparedSnapshotSyncOutput preparedOutput
    ) {
        return new CanvasNodeOperationResult(Map.of(), List.of(), List.of(), List.of(), preparedOutput, List.of(), List.of());
    }

    public static CanvasNodeOperationResult snapshotSyncOutput(
            CanvasPreparedSnapshotSyncOutput preparedOutput,
            CanvasLineageOutputCandidate lineageOutputCandidate
    ) {
        return new CanvasNodeOperationResult(
                Map.of(), List.of(), List.of(lineageOutputCandidate), List.of(), preparedOutput, List.of(), List.of());
    }

    public static CanvasNodeOperationResult kafkaOutput(CanvasPreparedKafkaOutput preparedOutput) {
        return new CanvasNodeOperationResult(Map.of(), List.of(), List.of(), List.of(), null,
                preparedOutput == null ? List.of() : List.of(preparedOutput), List.of());
    }

    public static CanvasNodeOperationResult kafkaOutput(
            CanvasPreparedKafkaOutput preparedOutput,
        CanvasLineageOutputCandidate lineageOutputCandidate
    ) {
        return new CanvasNodeOperationResult(
                Map.of(), List.of(), singleOrEmpty(lineageOutputCandidate), List.of(), null, singleOrEmpty(preparedOutput), List.of());
    }

    public static CanvasNodeOperationResult fileOutput(CanvasPreparedFileOutput preparedOutput) {
        return new CanvasNodeOperationResult(Map.of(), List.of(), List.of(), List.of(), null, List.of(),
                preparedOutput == null ? List.of() : List.of(preparedOutput));
    }

    public static CanvasNodeOperationResult fileOutput(
            CanvasPreparedFileOutput preparedOutput,
        CanvasLineageOutputCandidate lineageOutputCandidate
    ) {
        return new CanvasNodeOperationResult(
                Map.of(), List.of(), singleOrEmpty(lineageOutputCandidate), List.of(), null, List.of(), singleOrEmpty(preparedOutput));
    }

    public static CanvasNodeOperationResult outputOnly() {
        return output(null);
    }

    public static CanvasNodeOperationResult outputs(
            List<CanvasPreparedOutput> preparedOutputs,
            List<CanvasLineageOutputCandidate> lineageOutputCandidates
    ) {
        return new CanvasNodeOperationResult(Map.of(), List.of(), lineageOutputCandidates, preparedOutputs,
                null, List.of(), List.of());
    }

    public static CanvasNodeOperationResult kafkaOutputs(
            List<CanvasPreparedKafkaOutput> preparedOutputs,
            List<CanvasLineageOutputCandidate> lineageOutputCandidates
    ) {
        return new CanvasNodeOperationResult(Map.of(), List.of(), lineageOutputCandidates, List.of(),
                null, preparedOutputs, List.of());
    }

    public static CanvasNodeOperationResult fileOutputs(
            List<CanvasPreparedFileOutput> preparedOutputs,
            List<CanvasLineageOutputCandidate> lineageOutputCandidates
    ) {
        return new CanvasNodeOperationResult(Map.of(), List.of(), lineageOutputCandidates, List.of(),
                null, List.of(), preparedOutputs);
    }

    private static <T> List<T> singleOrEmpty(T value) {
        return value == null ? List.of() : List.of(value);
    }
}
