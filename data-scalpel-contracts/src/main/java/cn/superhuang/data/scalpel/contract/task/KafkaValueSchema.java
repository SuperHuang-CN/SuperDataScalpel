package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record KafkaValueSchema(List<KafkaValueColumn> columns) {
    public KafkaValueSchema {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
