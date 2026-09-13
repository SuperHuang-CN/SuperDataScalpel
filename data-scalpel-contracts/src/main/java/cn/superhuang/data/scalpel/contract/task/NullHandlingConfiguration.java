package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("支持 BATCH 和 STREAMING 的空值处理节点配置；对一个或多个不同上游逻辑表分别执行有序的 SQL NULL 删行或常量填充规则。每项操作可替换来源表或保留来源并创建新表，任一操作校验失败会使整个节点无效。空字符串、NaN 和零值不视为 NULL。")
public record NullHandlingConfiguration(
        @JsonPropertyDescription("独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。")
        List<NullHandlingOperation> operations
) {
    public NullHandlingConfiguration {
        operations = operations == null ? null : List.copyOf(operations);
    }
    public NullHandlingConfiguration(String sourceTableName, String outputTableName, List<NullHandlingRule> rules) {
        this(List.of(new NullHandlingOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.CreateNewTable(outputTableName), rules)));
    }
    private NullHandlingOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public List<NullHandlingRule> rules() { return single() == null ? null : single().rules(); }
}
