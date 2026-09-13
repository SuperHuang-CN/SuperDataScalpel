package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("模型输出配置；声明一项或多项到已发布纳管模型的独立写入。输出不产生下游表；多项写入按顺序执行且没有跨目标事务，前项可能已成功而后项失败。")

public record ModelOutputConfiguration(
        @JsonPropertyDescription("按数组顺序准备和执行的模型写入，至少一项；writeId 在节点内必须唯一。实时任务最多 32 项，每项作为独立流式查询按至少一次语义写入。任一批处理写入失败时，已完成项不回滚，后续项跳过。")
        List<ModelOutputWrite> writes
) {
    public ModelOutputConfiguration {
        writes = writes == null ? List.of() : List.copyOf(writes);
    }

    public ModelOutputConfiguration(
            String sourceTableName,
            String targetModelId,
            JdbcWriteMode writeMode,
            List<JdbcColumnMapping> columnMappings
    ) {
        this(List.of(new ModelOutputWrite(
                java.util.UUID.randomUUID().toString(), sourceTableName,
                targetModelId, writeMode, columnMappings)));
    }

    public String sourceTableName() { return writes.isEmpty() ? null : writes.getFirst().sourceTableName(); }
    public String targetModelId() { return writes.isEmpty() ? null : writes.getFirst().targetModelId(); }
    public JdbcWriteMode writeMode() { return writes.isEmpty() ? null : writes.getFirst().writeMode(); }
    public List<JdbcColumnMapping> columnMappings() { return writes.isEmpty() ? List.of() : writes.getFirst().columnMappings(); }
}
