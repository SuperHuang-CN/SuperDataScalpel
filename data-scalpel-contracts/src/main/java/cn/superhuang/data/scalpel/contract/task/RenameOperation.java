package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("对一张逻辑表执行一次原子表名和字段名变换；所有字段映射都基于原始 Schema 同时计算，可安全交换字段名。字段类型及其他元数据、表来源和有界性保持不变；无实际变化时仍可执行但产生警告。")
public record RenameOperation(
        @JsonPropertyDescription("当前操作的稳定 UUID，在本节点内唯一，用于关联配置、校验问题和字段血缘；不能使用空值或任意业务名称替代。")
        String operationId,
        @JsonPropertyDescription("进入本节点前已经存在的上游 Canvas 逻辑表名；同一节点内每张来源表最多配置一次。")
        String sourceTableName,
        @JsonPropertyDescription("结果表处理方式。REPLACE_SOURCE 可通过 outputTableName 同时改逻辑表名，NULL 表示沿用来源名；CREATE_NEW_TABLE 保留来源并要求提供不冲突的新表名。")
        ProcessorOutput output,
        @JsonPropertyDescription("可为空的字段重命名映射数组；每个来源字段最多出现一次，来源字段必须存在，所有映射与未映射字段计算后的最终名称不得重复。同名映射仅产生无效果警告。")
        List<RenameColumnMapping> columnMappings
) implements ProcessorOperation {
    public RenameOperation {
        columnMappings = columnMappings == null ? null : List.copyOf(columnMappings);
    }
}
