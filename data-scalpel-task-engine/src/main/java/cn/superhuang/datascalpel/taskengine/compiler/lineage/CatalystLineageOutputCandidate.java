package cn.superhuang.datascalpel.taskengine.compiler.lineage;

import cn.superhuang.data.scalpel.contract.task.TaskLineageEvidence;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Producer-neutral output boundary paired with the exact Dataset handed to a writer. */
public record CatalystLineageOutputCandidate(
        String flowKey,
        String producerKey,
        String producerType,
        String outputTransformKey,
        Dataset<Row> dataset,
        TaskLineageEvidence.Asset asset,
        List<TargetField> targetFields
) {
    public CatalystLineageOutputCandidate {
        flowKey = required(flowKey, "flowKey");
        producerKey = required(producerKey, "producerKey");
        producerType = required(producerType, "producerType");
        outputTransformKey = required(outputTransformKey, "outputTransformKey");
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(asset, "asset");
        targetFields = targetFields == null ? List.of() : List.copyOf(targetFields);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + "不能为空");
        return value.trim();
    }

    public record TargetField(
            String localFieldKey,
            UUID modelFieldId,
            String columnCode,
            String columnName,
            TaskLineageEvidence.OutputEffect missingOutputEffect
    ) {
    }
}
