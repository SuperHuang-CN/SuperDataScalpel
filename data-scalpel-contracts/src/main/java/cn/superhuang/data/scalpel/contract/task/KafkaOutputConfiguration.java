package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record KafkaOutputConfiguration(
        String dataSourceId,
        List<KafkaOutputWrite> writes
) {
    public KafkaOutputConfiguration {
        writes = writes == null ? List.of() : List.copyOf(writes);
    }

    public String sourceTableName() { return writes.isEmpty() ? null : writes.getFirst().sourceTableName(); }
    public String topic() { return writes.isEmpty() ? null : writes.getFirst().topic(); }
    public KafkaValueSchema valueSchema() { return writes.isEmpty() ? null : writes.getFirst().valueSchema(); }
    public String keyColumnName() { return writes.isEmpty() ? null : writes.getFirst().keyColumnName(); }
    public List<JdbcColumnMapping> columnMappings() { return writes.isEmpty() ? List.of() : writes.getFirst().columnMappings(); }
}
