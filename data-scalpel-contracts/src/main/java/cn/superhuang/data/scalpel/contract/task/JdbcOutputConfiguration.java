package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("JDBC 输出配置；向同一个已启用且具有 DISTRIBUTION 用途的 JDBC 数据源声明一项或多项独立物理表写入。输出不产生下游表；多项写入分别执行且没有跨目标事务，前项可能已成功而后项失败。")

public record JdbcOutputConfiguration(
        @JsonPropertyDescription("接收全部写入的 JDBC 数据源 UUID 字符串；草稿可为空，编译前必须解析为已启用、连接类型为 JDBC 且具有 DISTRIBUTION 用途的数据源。")
        String dataSourceId,
        @JsonPropertyDescription("按数组顺序准备和执行的独立目标表写入，至少一项；writeId 在节点内必须唯一。实时任务最多 32 项，每项作为独立流式查询按至少一次语义写入。任一批处理写入失败时，已完成项不回滚，后续项跳过。")
        List<JdbcOutputWrite> writes
) {
    public JdbcOutputConfiguration {
        writes = writes == null ? List.of() : List.copyOf(writes);
    }

    public JdbcOutputConfiguration(
            String sourceTableName,
            String dataSourceId,
            String targetTableName,
            JdbcWriteMode writeMode,
            List<JdbcColumnMapping> columnMappings,
            List<String> upsertKeyColumns
    ) {
        this(dataSourceId, List.of(new JdbcOutputWrite(
                java.util.UUID.randomUUID().toString(), sourceTableName, targetTableName,
                writeMode, columnMappings, upsertKeyColumns)));
    }

    /** Transitional internal convenience accessors for the per-write operator path. */
    public String sourceTableName() { return writes.isEmpty() ? null : writes.getFirst().sourceTableName(); }
    public String targetTableName() { return writes.isEmpty() ? null : writes.getFirst().targetTableName(); }
    public JdbcWriteMode writeMode() { return writes.isEmpty() ? null : writes.getFirst().writeMode(); }
    public List<JdbcColumnMapping> columnMappings() { return writes.isEmpty() ? List.of() : writes.getFirst().columnMappings(); }
    public List<String> upsertKeyColumns() { return writes.isEmpty() ? List.of() : writes.getFirst().upsertKeyColumns(); }
}
