package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("流式 Kafka 输出配置；把一张或多张无界逻辑表编码后写入同一 Kafka 数据源的最多 32 个独立 Topic Sink。每个写入拥有稳定 writeId，并由 Runner 使用独立 StreamingQuery 和 Checkpoint。")

public record KafkaOutputConfiguration(
        @JsonPropertyDescription("必填的 Kafka 数据源 UUID 字符串；编译和运行时必须对应已启用、具有 DISTRIBUTION 用途的 Kafka 连接。Broker 和凭据不进入 Canvas。")
        String dataSourceId,
        @JsonPropertyDescription("必填的 1 至 32 项 Topic 写入；按配置顺序准备，每项 writeId 必须是节点内唯一 UUID。NULL 列表规范化为空列表并在编译时作为缺失配置拒绝。")
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
