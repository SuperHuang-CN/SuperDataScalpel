package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.Objects;

public record CanvasPreparedKafkaOutput(
        KafkaOutputNodeDefinition node,
        String writeId,
        RuntimeDataSource runtimeDataSource,
        String topic,
        Dataset<Row> dataset
) {
    public CanvasPreparedKafkaOutput {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(writeId, "writeId");
        Objects.requireNonNull(runtimeDataSource, "runtimeDataSource");
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Kafka output topic is required");
        }
        Objects.requireNonNull(dataset, "dataset");
    }
}
