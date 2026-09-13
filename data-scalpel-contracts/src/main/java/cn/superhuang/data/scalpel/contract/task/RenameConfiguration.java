package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("支持 BATCH 和 STREAMING 的表及字段重命名配置；对一个或多个不同上游表分别以单次投影原子计算最终字段名，因此字段交换不受映射顺序影响。未处理表透传，任一操作校验失败会使整个节点无效。")
public record RenameConfiguration(
        @JsonPropertyDescription("独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。")
        List<RenameOperation> operations
) {
    public RenameConfiguration {
        operations = operations == null ? null : List.copyOf(operations);
    }
    public RenameConfiguration(String sourceTableName, String outputTableName, List<RenameColumnMapping> columnMappings) {
        this(List.of(new RenameOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.ReplaceSource(outputTableName), columnMappings)));
    }
    private RenameOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public List<RenameColumnMapping> columnMappings() { return single() == null ? null : single().columnMappings(); }
}
