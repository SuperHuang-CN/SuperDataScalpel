package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("支持 BATCH 和 STREAMING 的小型静态精确值映射配置；对一个或多个不同上游逻辑表分别执行字段内联映射，保持字段类型、名称和顺序。它不支持范围、正则、模糊或远程字典匹配，任一操作校验失败会使整个节点无效。")
public record ValueMappingConfiguration(
        @JsonPropertyDescription("独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。")
        List<ValueMappingOperation> operations
) {
    public ValueMappingConfiguration {
        operations = operations == null ? null : List.copyOf(operations);
    }
    public ValueMappingConfiguration(String sourceTableName, String outputTableName, List<ValueMappingRule> rules) {
        this(List.of(new ValueMappingOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.CreateNewTable(outputTableName), rules)));
    }
    private ValueMappingOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public List<ValueMappingRule> rules() { return single() == null ? null : single().rules(); }
}
